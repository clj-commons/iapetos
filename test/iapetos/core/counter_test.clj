(ns iapetos.core.counter-test
  (:require [clojure.test.check
             [generators :as gen]
             [properties :as prop]
             [clojure-test :refer [defspec]]]
            [iapetos.test.generators :as g]
            [iapetos.core :as prometheus]))

;; ## Counter w/o Labels

(def gen-incrementers
  (gen/vector
    (gen/let [amount (gen/fmap #(Math/abs ^double %)
                               (gen/double* {:infinite? false, :NaN? false}))]
      (gen/elements
        [{:f      #(prometheus/inc %1 %2 amount)
          :form   '(inc registry metric ~amount)
          :amount amount}
         {:f      #(prometheus/inc (%1 %2) amount)
          :form   '(inc (registry metric) ~amount)
          :amount amount}
         {:f      #(prometheus/inc %1 %2)
          :form   '(inc registry metric)
          :amount 1.0}
         {:f      #(prometheus/inc (%1 %2))
          :form   '(inc (registry metric))
          :amount 1.0}]))))

(defspec t-counter 100
  (prop/for-all
    [metric       g/metric
     incrementers gen-incrementers]
    (let [registry (-> (prometheus/collector-registry)
                       (prometheus/register
                         (prometheus/counter metric)))
          expected-value (double (reduce + (map :amount incrementers)))]
      (doseq [{:keys [f]} incrementers]
        (f registry metric))
      (= expected-value (prometheus/value (registry metric))))))

;; ## Counter w/ Labels

(def labels
  {:label "x"})

(def gen-incrementers-with-labels
  (gen/vector
    (gen/let [amount (gen/fmap #(Math/abs ^double %)
                               (gen/double* {:infinite? false, :NaN? false}))]
      (gen/elements
        [{:f      #(prometheus/inc %1 %2 labels amount)
          :form   '(inc registry metric labels ~amount)
          :amount amount}
         {:f      #(prometheus/inc (%1 %2 labels) amount)
          :form   '(inc (registry metric labels) ~amount)
          :amount amount}
         {:f      #(prometheus/inc %1 %2 labels)
          :form   '(inc registry metric labels)
          :amount 1.0}
         {:f      #(prometheus/inc (%1 %2 labels))
          :form   '(inc (registry metric labels))
          :amount 1.0}]))))

(defspec t-counter-with-labels 100
  (prop/for-all
    [metric       g/metric
     incrementers gen-incrementers-with-labels]
    (let [registry (-> (prometheus/collector-registry)
                       (prometheus/register
                         (prometheus/counter metric {:labels (keys labels)})))
          expected-value (double (reduce + (map :amount incrementers)))]
      (doseq [{:keys [f]} incrementers]
        (f registry metric))
      (= expected-value (prometheus/value (registry metric labels))))))
