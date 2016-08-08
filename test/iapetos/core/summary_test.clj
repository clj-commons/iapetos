(ns iapetos.core.summary-test
  (:require [clojure.test.check
             [generators :as gen]
             [properties :as prop]
             [clojure-test :refer [defspec]]]
            [clojure.test :refer :all]
            [iapetos.test.generators :as g]
            [iapetos.core :as prometheus]))

;; ## Summary w/o Labels

(def gen-ops
  (gen/vector
    (gen/let [v (gen/double* {:infinite? false, :NaN? false})]
      (let [amount (Math/abs ^double v)
            effect #(-> %
                        (update :count inc)
                        (update :sum   + amount))]
        (gen/elements
          [{:f      #(prometheus/observe %1 %2 amount)
            :form   '(observe registry metric ~amount)
            :effect effect}
           {:f      #(prometheus/observe (%1 %2) amount)
            :form   '(observe (registry metric) ~amount)
            :effect effect}])))))

(defspec t-summary 100
  (prop/for-all
    [metric      g/metric
     ops         gen-ops
     registry-fn (g/registry-fn)]
    (let [registry (-> (registry-fn)
                       (prometheus/register
                         (prometheus/summary metric)))
          expected-value (reduce
                           #((:effect %2) %1)
                           {:count 0.0, :sum 0.0}
                           ops)]
      (doseq [{:keys [f]} ops]
        (f registry metric))
      (= expected-value (prometheus/value (registry metric))))))

;; ## Summary w/ Labels

(def labels
  {:label "x"})

(def gen-ops
  (gen/vector
    (gen/let [v (gen/double* {:infinite? false, :NaN? false})]
      (let [amount (Math/abs ^double v)
            effect #(-> %
                        (update :count inc)
                        (update :sum   + amount))]
        (gen/elements
          [{:f      #(prometheus/observe %1 %2 labels amount)
            :form   '(observe registry metric labels ~amount)
            :effect effect}
           {:f      #(prometheus/observe (%1 %2 labels) amount)
            :form   '(observe (registry metric labels) ~amount)
            :effect effect}])))))

(defspec t-summary-with-labels 100
  (prop/for-all
    [metric      g/metric
     ops         gen-ops
     registry-fn (g/registry-fn)]
    (let [registry (-> (registry-fn)
                       (prometheus/register
                         (prometheus/summary metric {:labels (keys labels)})))
          expected-value (reduce
                           #((:effect %2) %1)
                           {:count 0.0, :sum 0.0}
                           ops)]
      (doseq [{:keys [f]} ops]
        (f registry metric))
      (= expected-value (prometheus/value (registry metric labels))))))

;; ## Summary Timer

(defspec t-summary-timer 5
  (prop/for-all
    [registry-fn (g/registry-fn)]
    (let [metric :app/duration-seconds
          registry (-> (registry-fn)
                       (prometheus/register
                         (prometheus/summary metric {:lazy? true})))
          start      (System/nanoTime)
          stop-timer (prometheus/start-timer registry metric)
          _          (do (Thread/sleep 50) (stop-timer))
          delta      (/ (- (System/nanoTime) start) 1e9)
          {:keys [sum count]} (prometheus/value (registry metric))]
      (and (= count 1.0)
           (<= 0.05 sum delta)))))
