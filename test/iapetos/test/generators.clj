(ns iapetos.test.generators
  (:require [clojure.test.check.generators :as gen]
            [iapetos.core :as prometheus]
            [iapetos.export :refer [pushable-collector-registry]]
            [clojure.string :as string]))

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

;; ## Name

(def valid-name
  (gen/let [first-char gen/char-alpha
            parts      (gen/vector (gen/not-empty gen/string-alpha-numeric))]
    (apply str first-char (string/join "_" parts))))

;; ## Registry

(defn registry-fn
  [& initializers]
  (gen/let [registry-name valid-name
            base-fn (gen/elements
                      [#(prometheus/collector-registry %)
                       #(pushable-collector-registry
                          {:job %
                           :push-gateway "0:8080"})])]
    (gen/return
      (fn []
        (reduce
          (fn [r f]
            (f r))
          (base-fn registry-name)
          initializers)))))

;; ## Collector

(defn collector
  ([collector-fn]
   (collector collector-fn (registry-fn)))
  ([collector-fn registry-fn-gen]
   (gen/let [metric      metric
             registry-fn registry-fn-gen]
     (let [collector (collector-fn metric)
           registry (prometheus/register (registry-fn) collector)]
       (gen/return (registry metric))))))

(def collectors
  (gen/vector
    (gen/let [metric metric
              metric-fn (gen/elements
                          [prometheus/counter
                           prometheus/gauge
                           prometheus/histogram
                           prometheus/summary])]
      (gen/return
        (metric-fn metric)))))
