(ns iapetos.test.generators
  (:require [clojure.test.check.generators :as gen]))

;; ## Metric

(def separator
  (->> (gen/elements [\- \_ \.])
       (gen/vector)
       (gen/not-empty)
       (gen/fmap #(apply str %))))

(def metric-namespace
  (gen/not-empty gen/string-ascii))

(def metric-string
  (gen/let [name-parts (gen/not-empty (gen/vector gen/string-ascii))
            separators (gen/vector separator (count name-parts))]
    (gen/return
      (apply str (interleave name-parts separators)))))

(def metric-keyword
  (gen/let [namespace metric-namespace
            name      metric-string]
    (gen/return (keyword namespace name))))

(def metric-vector
  (gen/tuple metric-namespace metric-string))

(def metric-map
  (gen/hash-map
    :namespace metric-namespace
    :name      metric-string))

(def metric
  (gen/one-of
    [metric-keyword
     metric-map
     metric-string
     metric-vector]))
