(ns jansuran03.task-scheduler.core
  (:require [clojure.core.async :as a]
            [jansuran03.task-scheduler.priority-queue :as pq]
            [jansuran03.task-scheduler.util :as util]))

(defn create-scheduler
  "Creates an asynchronous scheduler.

  Returns a map of
  - :schedule-fn - a function of [id f timeout], where
      - `id` is the task id, ideally a keyword/number
      - `f` is a function of 0 arguments that will be called after
      - `timeout` milliseconds
      - @return: a promise indicating, whether the scheduling succeeded (the task wasn't there yet) or not
  - :schedule-interval-fn - a function of [id f interval] same as with :schedule-fn
      - except the function is rescheduled after the interval in a loop
  - :cancel-schedule-fn - a function of [id] that cancels a scheduled task
      - @return: a promise indicating, whether the cancellation succeeded, or not
  - :wait-fn - a zero-argument function which blocks the current thread until the task queue is processed
      - disables interval schedules, but waits for the pending ones to finish
  - :stop-fn - a zero-argument function which stops the scheduler's job, no matter what's currently planned

  TODO: :wait-fn actually waits for all the jobs to start being executed, not for them to finish"
  []
  (with-local-vars [hierarchy (make-hierarchy)]
    (let [task-queue (atom (pq/create #(< (:scheduled-at %1) (:scheduled-at %2)) :id))
          signal-channel (a/chan 50)
          promising-put (fn [signal task]
                          (let [p (promise)]
                            (a/put! signal-channel [signal (assoc task :promise p)])
                            p))
          wait-channel (a/chan 1)
          wait-requested? (atom nil)
          stop-fn (fn stop-fn []
                    (a/put! signal-channel [::stop]))
          wait-fn (fn wait-fn []
                    (do (a/put! signal-channel [::wait])
                        (a/<!! wait-channel)))
          schedule-fn (fn schedule-task-fn [id f timeout]
                        (promising-put ::schedule {:f f :timeout timeout :id id}))
          schedule-interval-fn (fn schedule-interval-fn [id f interval]
                                 (promising-put ::schedule-interval {:f f :interval interval :id id}))
          cancel-schedule-fn (fn [id]
                               (let [p (promise)]
                                 (a/put! signal-channel [::cancel [id p]])
                                 p))
          run-task (fn run-task []
                     (let [[new-queue {:keys [f] :as task}] (pq/extract-min @task-queue)]
                       (a/go (f))
                       (reset! task-queue new-queue)
                       (when (and (:interval? task) (not @wait-requested?))
                         (a/go (a/put! signal-channel [::schedule-interval task])))))
          close-chans! (fn close-chans! []
                         (a/close! signal-channel)
                         (a/close! wait-channel))
          signal-handler (util/local-multifn first hierarchy)]

      (defmethod signal-handler ::stop [_] ::break)

      (defmethod signal-handler ::wait [_]
        (reset! wait-requested? true))

      (defmethod signal-handler ::schedule [[_ task]]
        (if-let [new-task-queue (pq/insert @task-queue (assoc task :scheduled-at (+ (System/currentTimeMillis)
                                                                                    (:timeout task))))]
          (do (reset! task-queue new-task-queue)
              (deliver (:promise task) true))
          (deliver (:promise task) false)))

      (defmethod signal-handler ::schedule-interval [[_ task]]
        (if-let [new-task-queue (pq/insert @task-queue (assoc task :scheduled-at (+ (System/currentTimeMillis)
                                                                                    (:interval task))
                                                                   :interval? true))]
          (do (reset! task-queue new-task-queue)
              (deliver (:promise task) true))
          (deliver (:promise task) false)))

      (defmethod signal-handler ::cancel [[_ [id p]]]
        (if-let [[new-task-queue _] (pq/remove-by-id @task-queue id)]
          (do (reset! task-queue new-task-queue)
              (deliver p true))
          (deliver p false)))

      (a/go (try
              (loop []
                (if (pq/queue-empty? @task-queue)
                  (if @wait-requested?
                    (a/>! wait-channel true)
                    (let [[signal _] (a/alts! [(a/chan) signal-channel])]
                      (when-not (identical? (signal-handler signal) ::break)
                        (recur))))
                  (let [delay-millis (- (:scheduled-at (pq/get-min @task-queue))
                                        (System/currentTimeMillis))]
                    (if (> delay-millis 0)
                      (let [timeout-chan (a/timeout delay-millis)
                            [signal port] (a/alts! [timeout-chan signal-channel])]
                        (if (identical? port timeout-chan)
                          (do (run-task)
                              (recur))
                          (when-not (identical? (signal-handler signal) ::break)
                            (recur))))
                      (do (run-task)
                          (recur))))))
              (finally
                (close-chans!))))
      {:stop-fn              stop-fn
       :schedule-fn          schedule-fn
       :schedule-interval-fn schedule-interval-fn
       :wait-fn              wait-fn
       :cancel-schedule-fn   cancel-schedule-fn})))
