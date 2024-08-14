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
            (a/go (schedule-fn #(swap! data conj uuid) timeout)))
          ; wait for everything to be scheduled
          (Thread/sleep 500)
          (wait-fn)
          (= @data (map second timeout-uuid-pairs))))))

(deftest interval-scheduler
  (let [{:keys [stop-fn schedule-interval-fn]} (scheduler/create-scheduler)
        data (atom [])]
    (a/go (schedule-interval-fn #(swap! data conj ::foo) 100))
    (Thread/sleep 250)
    (stop-fn)
    (is (= @data [::foo ::foo])))

  (let [{:keys [wait-fn schedule-interval-fn]} (scheduler/create-scheduler)
        data (atom [])]
    (a/go (schedule-interval-fn #(swap! data conj ::foo) 100))
    (Thread/sleep 250)
    (wait-fn)
    (is (= @data [::foo ::foo ::foo]))))
