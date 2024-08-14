(ns jansuran03.task-scheduler.core
  (:require [clojure.core.async :as a]
            [jansuran03.task-scheduler.priority-queue :as pq]))

(defn create-scheduler
  "Creates an asynchronous scheduler.

  Returns a map of
  - :schedule-fn - a function of [f timeout], where `f` is a function of 0 arguments that will be called after `timeout` milliseconds
  - :schedule-interval-fn - a function of [f interval] same as with :schedule-fn, except the function is rescheduled after the interval in a loop
  - :wait-fn - a zero-argument function which blocks the current thread until the task queue is processed
             - disables interval schedules, but waits for the pending ones to finish
  - :stop-fn - a zero-argument function which stops the scheduler's job, no matter what's currently planned

  TODO: :wait-fn actually waits for all the jobs to start being executed, not for them to finish"
  []
  (let [task-queue (atom (pq/create #(< (:scheduled-at %1) (:scheduled-at %2))))
        signal-channel (a/chan 50)
        wait-channel (a/chan 1)
        wait-requested? (atom nil)
        stop-fn (fn stop-fn []
                  (a/put! signal-channel [::stop]))
        wait-fn (fn wait-fn []
                  (do (a/put! signal-channel [::wait])
                      (a/<!! wait-channel)))
        schedule-task-fn (fn schedule-task-fn [f timeout]
                           (a/put! signal-channel [::schedule {:f       f
                                                               :timeout timeout}]))
        schedule (fn schedule [task]
                   (swap! task-queue pq/insert (assoc task :scheduled-at (+ (System/currentTimeMillis)
                                                                            (:timeout task)))))
        schedule-interval-fn (fn schedule-interval-fn [f interval]
                               (a/put! signal-channel [::schedule-interval {:f        f
                                                                            :interval interval}]))
        schedule-interval (fn schedule-interval [task]
                            (swap! task-queue pq/insert (assoc task :scheduled-at (+ (System/currentTimeMillis)
                                                                                     (:interval task))
                                                                    :interval? true)))
        run-task (fn run-task []
                   (let [[new-queue {:keys [f] :as task}] (pq/extract-min @task-queue)]
                     (a/go (f))
                     (reset! task-queue new-queue)
                     (when (and (:interval? task) (not @wait-requested?))
                       (schedule-interval task))))
        close-chans! (fn close-chans! []
                       (a/close! signal-channel)
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
                                     (recur))
                      ::schedule-interval (do (schedule-interval data)
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
                          ::wait (do (reset! wait-requested? true)
                                     (recur))
                          ::schedule (do (schedule data)
                                         (recur))
                          ::schedule-interval (do (schedule-interval data)
                                                  (recur)))))
                    (do (run-task)
                        (recur))))))
            (finally
              (close-chans!))))
    {:stop-fn              stop-fn
     :schedule-fn          schedule-task-fn
     :schedule-interval-fn schedule-interval-fn
     :wait-fn              wait-fn}))
