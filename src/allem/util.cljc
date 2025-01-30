(ns allem.util
  (:require [clojure.pprint]))

(defn single [[e & es]]
  (assert (nil? es) (str "Expected length 0 or 1, got " (count es)))
  e)

(defn spprint [d]
  (with-out-str
    (clojure.pprint/pprint d)))

(defn tap-> [v]
  (doto v tap>))
