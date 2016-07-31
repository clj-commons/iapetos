(ns iapetos.core.histogram-test
  (:require [clojure.test.check
             [generators :as gen]
             [properties :as prop]
             [clojure-test :refer [defspec]]]
            [clojure.test :refer :all]
            [iapetos.test.generators :as g]
            [iapetos.core :as prometheus]))

;; ## Helpers

(def buckets
  [1 2 3])

(defn- ->effect
  [amount]
  #(-> %
       (cond->
         (<= amount 1.0) (update-in [:buckets 0] inc)
         (<= amount 2.0) (update-in [:buckets 1] inc)
         (<= amount 3.0) (update-in [:buckets 2] inc)
         (<= amount 4.0) (update-in [:buckets 3] inc))
       (update :count inc)
       (update :sum   + amount)))

;; ## Summary w/o Labels

(def gen-ops
  (gen/vector
    (gen/let [amount (gen/double*
                       {:infinite? false, :NaN? false, :min 0.0, :max 4.0})]
      (let [effect (->effect amount)]
        (gen/elements
          [{:f      #(prometheus/observe %1 %2 amount)
            :form   '(observe registry metric ~amount)
            :effect effect}
           {:f      #(prometheus/observe (%1 %2) amount)
            :form   '(observe (registry metric) ~amount)
            :effect effect}])))))

(defspec t-histogram 100
  (prop/for-all
    [metric g/metric
     ops    gen-ops]
    (let [registry (-> (prometheus/collector-registry)
                       (prometheus/register
                         (prometheus/histogram metric {:buckets buckets})))
          expected-value (reduce
                           #((:effect %2) %1)
                           {:buckets [0.0 0.0 0.0 0.0], :count 0.0, :sum 0.0}
                           ops)]
      (doseq [{:keys [f]} ops]
        (f registry metric))
      (= expected-value (prometheus/value (registry metric))))))

;; ## Summary w/ Labels

(def labels
  {:label "x"})

(def gen-ops
  (gen/vector
    (gen/let [amount (gen/double*
                       {:infinite? false, :NaN? false, :min 0.0, :max 4.0})]
      (let [effect (->effect amount)]
        (gen/elements
          [{:f      #(prometheus/observe %1 %2 labels amount)
            :form   '(observe registry metric labels ~amount)
            :effect effect}
           {:f      #(prometheus/observe (%1 %2 labels) amount)
            :form   '(observe (registry metric labels) ~amount)
            :effect effect}])))))

(defspec t-histogram-with-labels 100
  (prop/for-all
    [metric g/metric
     ops    gen-ops]
    (let [registry (-> (prometheus/collector-registry)
                       (prometheus/register
                         (prometheus/histogram
                           metric
                           {:labels (keys labels), :buckets buckets})))
          expected-value (reduce
                           #((:effect %2) %1)
                           {:buckets [0.0 0.0 0.0 0.0], :count 0.0, :sum 0.0}
                           ops)]
      (doseq [{:keys [f]} ops]
        (f registry metric))
      (= expected-value (prometheus/value (registry metric labels))))))

;; ## Histogram Timer

(deftest t-histogram-timer
  (let [metric :app/duration-seconds
        registry (-> (prometheus/collector-registry)
                     (prometheus/register
                       (prometheus/histogram
                         metric
                         {:buckets [0.01 0.05 0.5]})))
        start      (System/nanoTime)
        stop-timer (prometheus/start-timer registry metric)
        _          (do (Thread/sleep 50) (stop-timer))
        delta      (/ (- (System/nanoTime) start) 1e9)
        {:keys [count sum buckets]} (prometheus/value (registry metric))]
    (is (= 1.0 count))
    (is (= [0.0 0.0 1.0 1.0] buckets))
    (is (<= 0.05 sum delta))))
