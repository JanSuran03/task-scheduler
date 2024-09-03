(ns jansuran03.task-scheduler.core
  (:require [clojure.core.async :as a]
            [jansuran03.task-scheduler.priority-queue :as pq]
            [jansuran03.task-scheduler.util :as util]))

(defprotocol IScheduler
  (wait-for-tasks [this]
    "Waits for all scheduled tasks to start being executed and to complete, blocks the current thread until then.
    Prevents interval tasks from rescheduling.")
  (stop [this]
    "Immediately cancels all scheduled tasks (does not cancel any currently being executed).
    Does not block the current thread until all running tasks complete.")
  (stop-and-wait [this]
    "A combination of `stop` & `wait-for-tasks`.
    Immediately cancels all scheduled tasks and blocks the current thread until all currently running complete.")
  (schedule [this id f timeout-millis]
    "Schedules a task (function `f` of 0 arguments) for execution after given millisecond timeout.
    If a scheduled task with this ID already exists, replaces it with this one.")
  (schedule-new [this id f timeout-millis]
    "Schedules a task (function `f` of 0 arguments) for execution after given millisecond timeout.
    Returns a promise indicating, whether the operation succeeded.
    If a task with this ID is already scheduled, delivers false to the promise,
    otherwise delivers true and schedules the task.")
  (schedule-interval [this id f interval-millis]
    "Schedules a task (function `f` of 0 arguments) for execution after given millisecond timeout,
    after starting the execution, the task is rescheduled again.")
  (schedule-new-interval [this id f interval-millis]
    "Schedules a task (function `f` of 0 arguments) for execution after given millisecond timeout,
    after starting the execution, the task is rescheduled again.
    Returns a promise indicating, whether the operation succeeded.
    If a task with this ID is already scheduled, delivers false to the promise,
    otherwise delivers true and schedules the task.")
  (cancel-schedule [this id]
    "Cancels a scheduled task. Returns a promise indicating, whether the task was canceled successfully."))

(defn create-scheduler
  "Creates an asynchronous scheduler, returning an object implementing the ISchedulerInterface.

  Opts:
  - :exec-fn` - a function that is responsible for executing scheduled tasks, takes 1-argument,
    which is the 0-argument function `f` to be executed via calling (exec-fn f).
    The function must not be blocking!! Examples: `#(a/go (%))`, `#(future-call (%))`"
  ([] (create-scheduler {}))
  ([{:keys [exec-fn]
     :or   {exec-fn (fn [f] (a/go (f)))}
     :as   opts}]
   (let [task-queue (atom (pq/create #(< (:scheduled-at %1) (:scheduled-at %2)) :id))
         task-counter (atom 0)
         signal-channel (a/chan 50)
         promising-put (fn [signal task]
                         (let [p (promise)]
                           (a/put! signal-channel [signal (assoc task :promise p)])
                           p))
         wait-channel (a/chan 1)
         wait-requested? (atom nil)
         all-scheduled? (atom false)
         release-pending-wait! #(when (and @wait-requested? @all-scheduled? (zero? @task-counter))
                                  (a/put! wait-channel true))
         run-task (fn run-task []
                    (let [[new-queue {:keys [f] :as task}] (pq/extract-min @task-queue)]
                      (swap! task-counter inc)
                      (exec-fn (fn []
                                 (try
                                   (f)
                                   (finally
                                     (swap! task-counter dec)
                                     (release-pending-wait!)))))
                      (reset! task-queue new-queue)
                      (when (and (:interval? task) (not @wait-requested?))
                        (a/put! signal-channel [::schedule-interval task]))))
         signal-handler (util/local-multifn first)]

     (defmethod signal-handler ::stop [_] ::break)

     (defmethod signal-handler ::wait [_]
       (reset! wait-requested? true))

     (defmethod signal-handler ::stop-and-wait [_]
       (reset! wait-requested? true)
       (reset! all-scheduled? true)
       (release-pending-wait!)
       ::break)

     (defmethod signal-handler ::schedule-new [[_ task]]
       (if-let [new-task-queue (pq/insert @task-queue (assoc task :scheduled-at (+ (System/currentTimeMillis)
                                                                                   (:timeout task))))]
         (do (reset! task-queue new-task-queue)
             (deliver (:promise task) true))
         (deliver (:promise task) false)))

     (defmethod signal-handler ::schedule [[_ task]]
       (let [task (assoc task :scheduled-at (+ (System/currentTimeMillis)
                                               (:timeout task)))]
         (if (pq/find-by-id @task-queue (:id task))
           (swap! task-queue pq/update-by-id (:id task) (constantly task))
           (swap! task-queue pq/insert task))))

     (defmethod signal-handler ::schedule-new-interval [[_ task]]
       (if-let [new-task-queue (pq/insert @task-queue (assoc task :scheduled-at (+ (System/currentTimeMillis)
                                                                                   (:interval task))
                                                                  :interval? true))]
         (do (reset! task-queue new-task-queue)
             (deliver (:promise task) true))
         (deliver (:promise task) false)))

     (defmethod signal-handler ::schedule-interval [[_ task]]
       (let [task (assoc task :scheduled-at (+ (System/currentTimeMillis)
                                               (:interval task))
                              :interval? true)]
         (if (pq/find-by-id @task-queue (:id task))
           (swap! task-queue pq/update-by-id (:id task) (constantly task))
           (swap! task-queue pq/insert task))))

     (defmethod signal-handler ::cancel [[_ [id p]]]
       (if-let [[new-task-queue _] (pq/remove-by-id @task-queue id)]
         (do (reset! task-queue new-task-queue)
             (deliver p true))
         (deliver p false)))

     (a/go (try
             (loop []
               (if (pq/queue-empty? @task-queue)
                 (if @wait-requested?
                   (do (reset! all-scheduled? true)
                       (release-pending-wait!))
                   (let [signal (a/<! signal-channel)]
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
               (a/close! signal-channel))))
     (reify
       IScheduler
       (wait-for-tasks [this]
         (a/put! signal-channel [::wait])
         (a/<!! wait-channel))
       (stop [this]
         (a/put! signal-channel [::stop]))
       (stop-and-wait [this]
         (a/put! signal-channel [::stop-and-wait])
         (a/<!! wait-channel))
       (schedule [this id f timeout-millis]
         (a/put! signal-channel [::schedule {:id id :f f :timeout timeout-millis}]))
       (schedule-new [this id f timeout-millis]
         (promising-put ::schedule-new {:id id :f f :timeout timeout-millis}))
       (schedule-interval [this id f interval-millis]
         (a/put! signal-channel [::schedule-interval {:id id :f f :interval interval-millis}]))
       (schedule-new-interval [this id f interval-millis]
         (promising-put ::schedule-new-interval {:id id :f f :interval interval-millis}))
       (cancel-schedule [this id]
         (let [p (promise)]
           (a/put! signal-channel [::cancel [id p]])
           p))))))
