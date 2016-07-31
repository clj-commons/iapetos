(ns iapetos.test.generators
  (:require [clojure.test.check.generators :as gen]))

;; ## Metric

(def separator
  (->> (gen/elements [\- \_ \.])
       (gen/vector)
       (gen/not-empty)
       (gen/fmap #(apply str %))))

(def metric-string
  (gen/let [first-char gen/char-alpha
            last-char  gen/char-alpha-numeric
            rest-chars gen/string-ascii]
    (gen/return
      (str
        (apply str first-char rest-chars)
        last-char))))

(def invalid-metric-string
  (->> (gen/tuple gen/nat (gen/vector separator))
       (gen/fmap #(apply str (first %) (rest %)))))

(def metric-namespace
  metric-string)

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
