(ns iapetos.core.duration-macro-test
  (:require [clojure.test :refer :all]
            [clojure.test.check
             [generators :as gen]
             [properties :as prop]
             [clojure-test :refer [defspec]]]
            [iapetos.test.generators :as g]
            [iapetos.core :as prometheus]))

;; ## Generator

(def gen-timeable
  (gen/let [metric g/metric
            countable (gen/elements
                        [(prometheus/gauge     metric)
                         (prometheus/histogram metric)
                         (prometheus/summary   metric)])]
    (let [registry (-> (prometheus/collector-registry)
                       (prometheus/register countable))]
      (gen/return
        (vector
          (if (= (:type countable) :gauge)
            prometheus/value
            (comp :sum prometheus/value))
          (registry metric))))))

;; ## Tests

(defspec t-with-duration 25
  (prop/for-all
    [[get-fn timeable] gen-timeable]
    (let [start (System/nanoTime)
          _ (prometheus/with-duration timeable
              (Thread/sleep 10))
          delta (/ (- (System/nanoTime) start) 1e9)
          value (get-fn timeable)]
      (<= 0.01 value delta))))
