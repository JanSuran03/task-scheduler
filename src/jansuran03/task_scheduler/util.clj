(ns jansuran03.task-scheduler.util
  (:import (clojure.lang MultiFn)))

(defn local-multifn [dispatch-fn hierarchy]
  (MultiFn. (name (gensym (str "local_multifn_"))) dispatch-fn :default hierarchy))
