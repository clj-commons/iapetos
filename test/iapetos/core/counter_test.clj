(ns iapetos.core.counter-test
  (:require [clojure.test.check
             [generators :as gen]
             [properties :as prop]
             [clojure-test :refer [defspec]]]
            [iapetos.test.generators :as g]
            [iapetos.core :as prometheus]))

(def gen-incrementers
  (gen/vector
    (gen/let [amount (gen/fmap #(Math/abs %)
                               (gen/double* {:infinite? false, :NaN? false}))]
      (gen/elements
        [{:f      #(prometheus/inc %1 %2 amount)
          :form   (list 'inc 'registry 'metric amount)
          :amount amount}
         {:f      #(prometheus/inc (%1 %2) amount)
          :form   (list 'inc '(registry metric) amount)
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
