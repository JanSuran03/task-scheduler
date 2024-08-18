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
    (a/go (scheduler/schedule-interval scheduler (System/nanoTime) #(swap! data conj ::foo) 100))
    (Thread/sleep 250)
    (scheduler/stop scheduler)
    (is (= @data [::foo ::foo])))

  (let [scheduler (scheduler/create-scheduler)
        data (atom [])]
    (a/go (scheduler/schedule-interval scheduler (System/nanoTime) #(swap! data conj ::foo) 100))
    (Thread/sleep 250)
    (scheduler/wait-for-tasks scheduler)
    (is (= @data [::foo ::foo ::foo]))))

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
        schedule-500 (fn [] (swap! results conj @(scheduler/schedule-new-interval scheduler :id-700 #(swap! data conj 700) 700)))]
    (schedule-1000)
    (schedule-500)
    (schedule-1000)
    (schedule-500)
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
        scheduler (scheduler/create-scheduler {:exec-fn (fn [x] (swap! data conj x))})
        xs [300 600 200 400 500 100 700 900 800]]
    (doseq [x xs]
      (scheduler/schedule scheduler x x x))
    (scheduler/wait-for-tasks scheduler)
    (is (= @data (sort xs)))))
