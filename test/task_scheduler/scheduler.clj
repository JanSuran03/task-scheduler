(ns task-scheduler.scheduler
  (:require [clojure.test :refer :all]
            [clojure.core.async :as a]
            [jansuran03.task-scheduler.core :as scheduler]))

(deftest scheduler
  (let [num-uuids 50]
    (is (let [{:keys [schedule-fn wait-fn]} (scheduler/create-scheduler)
              data (atom [])
              timeout-uuid-pairs (->> (range num-uuids)
                                      (map #(* 100 %))
                                      (map #(vector % (random-uuid))))]
          (doseq [[timeout uuid] (shuffle timeout-uuid-pairs)]
            (a/go (schedule-fn uuid #(swap! data conj uuid) timeout)))
          ; wait for everything to be scheduled
          (Thread/sleep 500)
          (wait-fn)
          (= @data (map second timeout-uuid-pairs))))))

(deftest interval-scheduler
  (let [{:keys [stop-fn schedule-interval-fn]} (scheduler/create-scheduler)
        data (atom [])]
    (a/go (schedule-interval-fn (System/nanoTime) #(swap! data conj ::foo) 100))
    (Thread/sleep 250)
    (stop-fn)
    (is (= @data [::foo ::foo])))

  (let [{:keys [wait-fn schedule-interval-fn]} (scheduler/create-scheduler)
        data (atom [])]
    (a/go (schedule-interval-fn (System/nanoTime) #(swap! data conj ::foo) 100))
    (Thread/sleep 250)
    (wait-fn)
    (is (= @data [::foo ::foo ::foo]))))

(deftest schedule-result
  (let [{:keys [wait-fn schedule-fn]} (scheduler/create-scheduler)
        data (atom [])
        results (atom [])
        schedule-1000 (fn [] (swap! results conj @(schedule-fn :id-1000 #(swap! data conj 1000) 1000)))
        schedule-500 (fn [] (swap! results conj @(schedule-fn :id-500 #(swap! data conj 500) 500)))]
    (schedule-1000)
    (schedule-500)
    (schedule-1000)
    (schedule-500)
    (Thread/sleep 1100)
    (schedule-1000)
    (schedule-500)
    (schedule-1000)
    (schedule-500)
    (wait-fn)
    (is (= @data [500 1000 500 1000]))
    (is (= @results [true true false false true true false false])))

  (let [{:keys [stop-fn schedule-interval-fn]} (scheduler/create-scheduler)
        data (atom [])
        results (atom [])
        schedule-1000 (fn [] (swap! results conj @(schedule-interval-fn :id-1000 #(swap! data conj 1000) 1000)))
        schedule-500 (fn [] (swap! results conj @(schedule-interval-fn :id-700 #(swap! data conj 700) 700)))]
    (schedule-1000)
    (schedule-500)
    (schedule-1000)
    (schedule-500)
    (Thread/sleep 2900)
    (stop-fn)
    (is (= @results [true true false false]))
    (is (= @data [700 1000 700 1000 700 700]))))

(deftest cancel-schedule
  (let [p (promise)
        {:keys [cancel-schedule-fn schedule-fn stop-fn]} (scheduler/create-scheduler)
        _ (schedule-fn 42 #(deliver p 42) 42)
        results [@(cancel-schedule-fn 42)
                 @(cancel-schedule-fn 42)]]
    (is (= results [true false]))
    (is (= (deref p 100 ::none) ::none))
    (stop-fn)))
