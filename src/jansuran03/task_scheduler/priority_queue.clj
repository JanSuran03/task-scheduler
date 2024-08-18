(ns jansuran03.task-scheduler.priority-queue)

(defprotocol IPriorityQueue
  (get-min [queue]
    "Return an element `x` from the queue for which (not (lt y x)) is true
    for each other element `y` in the queue.

    Works in Θ(1).")
  (build [queue elements]
    "Build a priority queue out of the given unsorted elements, expecting
    the element at index 0 not to ever be accessed.

    Works in Θ(n).")
  (extract-min [queue]
    "Extract an element `x` from the queue for which (not (lt y x)) is true for each
    other element `y` in the queue. Returns a pair of [new-priority-queue extracted-min].

    Works in O(log(n)).")
  (insert [queue x]
    "Inserts a new element to the priority queue, keeping the heap ordering.

    Works in O(log(n))")
  (find-by-id [queue id]
    "Finds an element by its id.

    Works in Θ(1).")
  (remove-by-id [queue id]
    "Removes an element by its id. Returns a pair of [new-priority-queue removed-element].

    Works in O(log(n)).")
  (update-by-id [queue id f]
    "Updates an element by its id by applying function `f` to it.
    Returns a new priority queue fix fixed ordering, or nil if the element is not present.

    Do not affect the output of the id-fn on this element by this change, the priority queue
    is not responsible for fixing that!

    Works in O(log(n)).")
  (queue-empty? [queue]
    "Returns true iff the queue is empty."))

(defn- ^:no-doc bubble-down [[queue id->index lt id-fn] index]
  (let [size (count queue)]
    (loop [cur-index index
           queue (transient queue)
           id->index (transient id->index)]
      (if (< (bit-shift-left cur-index 1) size)
        (let [child-index (bit-shift-left cur-index 1)
              child-index (if (and (< child-index (dec size))
                                   (lt (queue (inc child-index)) (queue child-index)))
                            (inc child-index)
                            child-index)]
          (if (lt (queue child-index) (queue cur-index))
            (let [child (queue child-index)
                  cur-id (id-fn (queue cur-index))
                  child-id (id-fn child)]
              (recur child-index
                     (assoc! queue child-index (queue cur-index), cur-index child)
                     (assoc! id->index cur-id child-index, child-id cur-index)))
            [(persistent! queue) (persistent! id->index)]))
        [(persistent! queue) (persistent! id->index)]))))

(defn- ^:no-doc bubble-up [[queue id->index lt id-fn] index]
  (loop [cur-index index
         queue (transient queue)
         id->index (transient id->index)]
    (if (<= cur-index 1)
      [(persistent! queue) (persistent! id->index)]
      (let [parent-index (bit-shift-right cur-index 1)]
        ;(println cur-index parent-index)
        (if (lt (queue cur-index) (queue parent-index))
          (let [parent (queue parent-index)
                cur-id (id-fn (queue cur-index))
                parent-id (id-fn parent)]
            (recur parent-index
                   (assoc! queue parent-index (queue cur-index), cur-index parent)
                   (assoc! id->index cur-id parent-index, parent-id cur-index)))
          [(persistent! queue) (persistent! id->index)])))))

(defn- ^:no-doc fix-structure [[queue id->index lt id-fn :as pq-vec] index]
  (let [size (dec (count queue))
        elem (queue index)
        i-parent (bit-shift-right index 1)
        i-left-child (bit-shift-left index 1)
        i-right-child (inc i-left-child)
        [queue id->index] (cond (and (> index 1) (lt elem (queue i-parent)))
                                (bubble-up pq-vec index)

                                (or (and (<= i-left-child size) (lt (queue i-left-child) elem))
                                    (and (<= i-right-child size) (lt (queue i-right-child) elem)))
                                (bubble-down pq-vec index)

                                :else
                                [queue id->index])]
    [queue id->index]))

(defrecord PriorityQueue [queue id->index lt-cmp id-fn]
  IPriorityQueue
  (get-min [this]
    (second queue))
  (build [this elements]
    (let [[queue id->index] (reduce (fn [[queue id->index] index]
                                      (bubble-down [queue id->index lt-cmp id-fn] index))
                                    [(into [::no-item] elements) (into {} (map-indexed (fn [i x] [(id-fn x) (inc i)])) elements)]
                                    (range (bit-shift-right (count elements) 1) 0 -1))]
      (PriorityQueue. queue id->index lt-cmp id-fn)))
  (extract-min [this]
    (when-let [elem (second queue)]
      (let [[queue id->index'] (bubble-down [(-> queue (assoc 1 (peek queue)) pop)
                                             (cond-> id->index
                                                     true (dissoc (id-fn elem))
                                                     (> (count queue) 2) (assoc (id-fn (peek queue)) 1))
                                             lt-cmp
                                             id-fn] 1)]
        [(PriorityQueue. queue id->index' lt-cmp id-fn)
         elem])))
  (insert [this x]
    (when-not (id->index (id-fn x))
      (let [[queue id->index] (bubble-up [(conj queue x)
                                          (assoc id->index (id-fn x) (count queue))
                                          lt-cmp
                                          id-fn] (count queue))]
        (PriorityQueue. queue id->index lt-cmp id-fn))))
  (find-by-id [this id]
    (some-> (id->index id) queue))
  (remove-by-id [this id]
    (when-let [index (id->index id)]
      (if (= index (dec (count queue)))
        [(PriorityQueue. (pop queue) (dissoc id->index id) lt-cmp id-fn) (peek queue)]
        (let [elem (queue index)
              [queue id->index] [(-> queue (assoc index (peek queue)) pop)
                                 (-> id->index (dissoc id) (assoc (id-fn (peek queue)) index))]
              [queue id->index] (fix-structure [queue id->index lt-cmp id-fn] index)]
          [(PriorityQueue. queue id->index lt-cmp id-fn) elem]))))
  (update-by-id [this id f]
    (when-let [index (id->index id)]
      (let [[queue id->index] (fix-structure [(update queue index f) id->index lt-cmp id-fn] index)]
        (PriorityQueue. queue id->index lt-cmp id-fn))))
  (queue-empty? [this]
    (<= (count queue) 1)))

(defn create
  "Creates a priority queue with the given comparator, where an element `x` from
  the queue for which (not (lt y x)) is true for each other element `y` in the
  queue, will be treated as the minimum by `get-min` and `extract-min`."
  [lt-comparator id-fn]
  (PriorityQueue. [::no-item] {} lt-comparator id-fn))
