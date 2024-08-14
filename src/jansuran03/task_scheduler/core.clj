(ns jansuran03.task-scheduler.core
  (:require [clojure.core.async :as a]
            [jansuran03.task-scheduler.priority-queue :as pq]))

(defn create-scheduler []
  (let [task-queue (atom (pq/create #(< (:scheduled-at %1) (:scheduled-at %2))))
        signal-channel (a/chan 50)
        wait-channel (a/chan 1)
        wait-requested? (atom nil)
        stop-fn #(a/put! signal-channel [::stop])
        wait-fn #(do (a/put! signal-channel [::wait])
                     (a/<!! wait-channel))
        schedule-task-fn (fn [f timeout]
                           (a/put! signal-channel [::schedule {:f       f
                                                               :timeout timeout}]))
        schedule #(swap! task-queue pq/insert (assoc % :scheduled-at (+ (System/currentTimeMillis)
                                                                        (:timeout %))))
        run-task #(let [[new-queue {:keys [f]}] (pq/extract-min @task-queue)]
                    (reset! task-queue new-queue)
                    (a/go (f)))
        close-chans! #(do (a/close! signal-channel)
                          (a/close! wait-channel))]
    (a/go (try
            (loop []
              (if (pq/queue-empty? @task-queue)
                (if @wait-requested?
                  (a/>! wait-channel true)
                  (let [[[action data] _] (a/alts! [(a/chan) signal-channel])]
                    (case action
                      ::stop nil
                      ::wait (do (reset! wait-requested? true)
                                 (recur))
                      ::schedule (do (schedule data)
                                     (recur)))))
                (let [delay-millis (- (:scheduled-at (pq/get-min @task-queue))
                                      (System/currentTimeMillis))]
                  (if (> delay-millis 0)
                    (let [timeout-chan (a/timeout delay-millis)
                          [[action data] port] (a/alts! [timeout-chan signal-channel])]
                      (if (identical? port timeout-chan)
                        (do (run-task)
                            (recur))
                        (case action
                          ::stop nil
                          ::schedule (do (schedule data)
                                         (recur))
                          ::wait (do (reset! wait-requested? true)
                                     (recur)))))
                    (do (run-task)
                        (recur))))))
            (finally
              (close-chans!))))
    {:stop-fn     stop-fn
     :schedule-fn schedule-task-fn
     :wait-fn     wait-fn}))
