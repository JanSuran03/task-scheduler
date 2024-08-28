(ns task-scheduler.scheduler
  (:require [clojure.test :refer :all]
            [clojure.core.async :as a]
            [jansuran03.task-scheduler.core :as scheduler]))

(deftest scheduler
  (let [num-uuids 50]
    (is (let [scheduler (scheduler/create-scheduler)
              data (atom [])
              timeout-uuid-pairs (->> (range num-uuids)
                                      (map #(* 100 %))
                                      (map #(vector % (random-uuid))))]
          (doseq [[timeout uuid] (shuffle timeout-uuid-pairs)]
            (scheduler/schedule-new scheduler uuid #(swap! data conj uuid) timeout))
          (scheduler/wait-for-tasks scheduler)
          (= (map second timeout-uuid-pairs)
             @data)))))

(deftest interval-scheduler
  (let [scheduler (scheduler/create-scheduler)
        data (atom [])]
    (scheduler/schedule-interval scheduler (System/nanoTime) #(swap! data conj ::foo) 100)
    (Thread/sleep 250)
    (scheduler/stop scheduler)
    (is (= @data [::foo ::foo])))

  (let [scheduler (scheduler/create-scheduler)
        data (atom [])]
    (scheduler/schedule-interval scheduler (System/nanoTime) #(swap! data conj ::foo) 100)
    (Thread/sleep 250)
    (scheduler/wait-for-tasks scheduler)
    (is (= @data [::foo ::foo ::foo]))))

(deftest scheduler-wait
  (let [done (atom false)
        scheduler (scheduler/create-scheduler)]
    (scheduler/schedule scheduler 42 #(do (Thread/sleep 1000)
                                          (reset! done true)) -42)
    (scheduler/wait-for-tasks scheduler)
    (is @done)))

(deftest stop-and-wait
  (let [scheduler (scheduler/create-scheduler)
        data (atom [])]
    (scheduler/schedule scheduler :foo #(swap! data conj :foo) 500)
    (scheduler/schedule scheduler :bar #(swap! data conj :bar) 1000)
    (Thread/sleep 700)
    (scheduler/stop-and-wait scheduler)
    (is (= @data [:foo]))))

(deftest schedule-result
  (let [scheduler (scheduler/create-scheduler)
        data (atom [])
        results (atom [])
        schedule-1000 (fn [] (swap! results conj @(scheduler/schedule-new scheduler :id-1000 #(swap! data conj 1000) 1000)))
        schedule-500 (fn [] (swap! results conj @(scheduler/schedule-new scheduler :id-500 #(swap! data conj 500) 500)))]
    (schedule-1000)
    (schedule-500)
    (schedule-1000)
    (schedule-500)
    (Thread/sleep 1100)
    (schedule-1000)
    (schedule-500)
    (schedule-1000)
    (schedule-500)
    (scheduler/wait-for-tasks scheduler)
    (is (= @data [500 1000 500 1000]))
    (is (= @results [true true false false true true false false])))

  (let [scheduler (scheduler/create-scheduler)
        data (atom [])
        results (atom [])
        schedule-1000 (fn [] (swap! results conj @(scheduler/schedule-new-interval scheduler :id-1000 #(swap! data conj 1000) 1000)))
        schedule-700 (fn [] (swap! results conj @(scheduler/schedule-new-interval scheduler :id-700 #(swap! data conj 700) 700)))]
    (schedule-1000)
    (schedule-700)
    (schedule-1000)
    (schedule-700)
    (Thread/sleep 2900)
    (scheduler/stop scheduler)
    (is (= @results [true true false false]))
    (is (= @data [700 1000 700 1000 700 700]))))

(deftest cancel-schedule
  (let [p (promise)
        scheduler (scheduler/create-scheduler)
        results [@(scheduler/cancel-schedule scheduler 42)]
        _ (scheduler/schedule scheduler 42 #(deliver p 42) 42)
        results (conj results
                      @(scheduler/cancel-schedule scheduler 42)
                      @(scheduler/cancel-schedule scheduler 42))]
    (is (= results [false true false]))
    (is (= (deref p 100 ::none) ::none))
    (scheduler/stop scheduler)))

(deftest exec-fn
  (let [data (atom [])
        num-exec'd (atom 0)
        scheduler (scheduler/create-scheduler {:exec-fn (fn [f]
                                                          (swap! num-exec'd + 2)
                                                          (a/go (f)))})
        timeouts [300 600 200 400 500 100 700 900 800]]
    (doseq [timeout timeouts]
      (scheduler/schedule scheduler timeout (with-meta #(swap! data conj timeout) {:timeout timeout}) timeout))
    (scheduler/wait-for-tasks scheduler)
    (is (= @data (sort timeouts)))
    (is (= @num-exec'd (* 2 (count timeouts))))))

(deftest scheduler-exit-loop
  (let [scheduler (scheduler/create-scheduler)
        p (promise)]
    (scheduler/stop scheduler)
    (scheduler/schedule scheduler :foo #(deliver p 42) 50)
    (is (identical? (deref p 100 ::none) ::none)))

  (let [scheduler (scheduler/create-scheduler)
        p (promise)]
    (scheduler/wait-for-tasks scheduler)
    (scheduler/schedule scheduler :foo #(deliver p 42) 50)
    (is (identical? (deref p 100 ::none) ::none)))

  (let [scheduler (scheduler/create-scheduler)
        p (promise)]
    (scheduler/stop-and-wait scheduler)
    (scheduler/schedule scheduler :foo #(deliver p 42) 50)
    (is (identical? (deref p 100 ::none) ::none))))
