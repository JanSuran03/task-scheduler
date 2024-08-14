(ns task-scheduler.priority-queue
  (:require [clojure.test :refer :all]
            [jansuran03.task-scheduler.priority-queue :as pq]))

(defn heapsort [q]
  (loop [q q
         data []]
    (if-let [res (pq/extract-min q)]
      (recur (first res)
             (conj data (second res)))
      data)))

(defn test-heapsort [lt-cmp]
  (every? true? (for [size (range 1 30)
                      _ (range 5)
                      :let [random-data (repeatedly size #(rand-int 100))
                            from-array (pq/build (pq/create lt-cmp) random-data)
                            from-multi-insert (reduce pq/insert (pq/create lt-cmp) random-data)
                            std-sorted (sort lt-cmp random-data)]]
                  (= (heapsort from-array) (heapsort from-multi-insert) std-sorted))))

(deftest priority-queue
  (is (test-heapsort <))
  (is (test-heapsort >))
  )
