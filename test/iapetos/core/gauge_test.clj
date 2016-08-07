(ns iapetos.core.gauge-test
  (:require [clojure.test.check
             [generators :as gen]
             [properties :as prop]
             [clojure-test :refer [defspec]]]
            [clojure.test :refer :all]
            [iapetos.test.generators :as g]
            [iapetos.core :as prometheus]))

;; ## Gauge w/o Labels

(def gen-ops
  (gen/vector
    (gen/let [v (gen/double* {:infinite? false, :NaN? false})]
      (let [amount (Math/abs ^double v)]
        (gen/elements
          [{:f      #(prometheus/inc %1 %2 amount)
            :form   '(inc registry metric ~amount)
            :effect #(+ % amount)}
           {:f      #(prometheus/inc (%1 %2) amount)
            :form   '(inc (registry metric) ~amount)
            :effect #(+ % amount)}
           {:f      #(prometheus/inc %1 %2)
            :form   '(inc registry metric)
            :effect #(+ % 1.0)}
           {:f      #(prometheus/inc (%1 %2))
            :form   '(inc (registry metric))
            :effect #(+ % 1.0)}
           {:f      #(prometheus/dec %1 %2 amount)
            :form   '(dec registry metric ~amount)
            :effect #(- % amount)}
           {:f      #(prometheus/dec (%1 %2) amount)
            :form   '(dec (registry metric) ~amount)
            :effect #(- % amount)}
           {:f      #(prometheus/dec %1 %2)
            :form   '(dec registry metric)
            :effect #(- % 1.0)}
           {:f      #(prometheus/dec (%1 %2))
            :form   '(dec (registry metric))
            :effect #(- % 1.0)}
           {:f      #(prometheus/observe %1 %2 amount)
            :form   '(observe registry metric ~amount)
            :effect (constantly amount)}
           {:f      #(prometheus/observe (%1 %2) amount)
            :form   '(observe (registry metric) ~amount)
            :effect (constantly amount)}
           {:f      #(prometheus/set %1 %2 amount)
            :form   '(set registry metric ~amount)
            :effect (constantly amount)}
           {:f      #(prometheus/set (%1 %2) amount)
            :form   '(set (registry metric) ~amount)
            :effect (constantly amount)}])))))

(defspec t-gauge 100
  (prop/for-all
    [metric g/metric
     ops    gen-ops]
    (let [registry (-> (prometheus/collector-registry)
                       (prometheus/register
                         (prometheus/gauge metric)))
          expected-value (double (reduce #((:effect %2) %1) 0.0 ops))]
      (doseq [{:keys [f]} ops]
        (f registry metric))
      (= expected-value (prometheus/value (registry metric))))))

;; ## Gauge w/ Labels

(def labels
  {:label "x"})

(def gen-ops-with-labels
  (gen/vector
    (gen/let [v (gen/double* {:infinite? false, :NaN? false})]
      (let [amount (Math/abs ^double v)]
        (gen/elements
          [{:f      #(prometheus/inc %1 %2 labels amount)
            :form   '(inc registry metric labels ~amount)
            :effect #(+ % amount)}
           {:f      #(prometheus/inc (%1 %2 labels) amount)
            :form   '(inc (registry metric labels) ~amount)
            :effect #(+ % amount)}
           {:f      #(prometheus/inc %1 %2 labels)
            :form   '(inc registry metric labels)
            :effect #(+ % 1.0)}
           {:f      #(prometheus/inc (%1 %2 labels))
            :form   '(inc (registry metric labels))
            :effect #(+ % 1.0)}
           {:f      #(prometheus/dec %1 %2 labels amount)
            :form   '(dec registry metric labels ~amount)
            :effect #(- % amount)}
           {:f      #(prometheus/dec (%1 %2 labels) amount)
            :form   '(dec (registry metric labels) ~amount)
            :effect #(- % amount)}
           {:f      #(prometheus/dec %1 %2 labels)
            :form   '(dec registry metric labels)
            :effect #(- % 1.0)}
           {:f      #(prometheus/dec (%1 %2 labels))
            :form   '(dec (registry metric labels))
            :effect #(- % 1.0)}
           {:f      #(prometheus/observe %1 %2 labels amount)
            :form   '(observe registry metric labels ~amount)
            :effect (constantly amount)}
           {:f      #(prometheus/observe (%1 %2 labels) amount)
            :form   '(observe (registry metric labels) ~amount)
            :effect (constantly amount)}
           {:f      #(prometheus/set %1 %2 labels amount)
            :form   '(set registry metric labels ~amount)
            :effect (constantly amount)}
           {:f      #(prometheus/set (%1 %2 labels) amount)
            :form   '(set (registry metric labels) ~amount)
            :effect (constantly amount)}])))))

(defspec t-gauge-with-labels 100
  (prop/for-all
    [metric g/metric
     ops    gen-ops-with-labels]
    (let [registry (-> (prometheus/collector-registry)
                       (prometheus/register
                         (prometheus/gauge metric {:labels (keys labels)})))
          expected-value (double (reduce #((:effect %2) %1) 0.0 ops))]
      (doseq [{:keys [f]} ops]
        (f registry metric))
      (= expected-value (prometheus/value (registry metric labels))))))

;; ## Setting to Current Time

(def gen-set-to-current-time-op
  (gen/elements
    [{:f #(prometheus/set-to-current-time %1 %2)
      :form '(set-to-current-time registry metric)}
     {:f #(prometheus/set-to-current-time (%1 %2))
      :form '(set-to-current-time (registry metric))}]))

(defspec t-gauge-set-to-current-time 100
  (prop/for-all
    [metric g/metric
     op     gen-set-to-current-time-op]
    (let [registry (-> (prometheus/collector-registry)
                       (prometheus/register
                         (prometheus/gauge metric)))
          before (System/currentTimeMillis)
          _     ((:f op) registry metric)
          after (System/currentTimeMillis)]
      (<= before (* (prometheus/value (registry metric)) 1e3) after))))

;; ## Timer

(deftest t-gauge-timer
  (let [metric :app/duration-seconds
        registry (-> (prometheus/collector-registry)
                     (prometheus/register
                       (prometheus/gauge metric)))
        start      (System/nanoTime)
        stop-timer (prometheus/start-timer registry metric)
        _          (do (Thread/sleep 50) (stop-timer))
        delta      (/ (- (System/nanoTime) start) 1e9)]
    (is (<= 0.05 (prometheus/value (registry metric)) delta))))
