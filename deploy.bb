#!/usr/bin/env bb

(require '[clojure.java.shell :as sh])

(defn exec [cmd]
  (sh/sh "cmd" "/c" cmd))

(let [result (exec "lein test")]
  (if (zero? (:exit result))
    (exec "lein deploy clojars")
    (binding [*out* *err*]
      (println "Tests failed:")
      (println (:out result)))))
