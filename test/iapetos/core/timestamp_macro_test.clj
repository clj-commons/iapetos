(ns iapetos.core.timestamp-macro-test
  (:require [clojure.test :refer :all]
            [clojure.test.check
             [generators :as gen]
             [properties :as prop]
             [clojure-test :refer [defspec]]]
            [iapetos.test.generators :as g]
            [iapetos.core :as prometheus]))

;; ## Generator

(def gen-gauge
  (gen/let [metric g/metric
            labels (gen/map g/metric-string gen/string-alpha-numeric)
            registry-fn (g/registry-fn)]
    (let [registry (-> (registry-fn)
                       (prometheus/register
                         (prometheus/gauge metric {:labels (keys labels)})))]
      (gen/return (registry metric labels)))))

;; ## Helpers

(defn run-and-get!
  [f gauge]
  (let [start (System/currentTimeMillis)
        _ (try (f) (catch Throwable _))
        end (System/currentTimeMillis)]
    {:value (* (prometheus/value gauge) 1000)
     :start start
     :end   end}))

;; ## Tests

(defspec t-with-timestamp 25
  (prop/for-all
    [gauge gen-gauge]
    (let [{:keys [value start end]}
          (run-and-get!
            #(prometheus/with-timestamp gauge
               (comment 'do-something))
            gauge)]
      (<= start value end))))

(defspec t-with-failure-timestamp 25
  (prop/for-all
    [gauge gen-gauge]
    (let [{:keys [value start end]}
          (run-and-get!
            #(prometheus/with-failure-timestamp gauge
               (throw (Exception.)))
            gauge)]
      (<= start value end))))

(defspec t-with-failure-timestamp-but-success 25
  (prop/for-all
    [gauge gen-gauge]
    (let [{:keys [value]}
          (run-and-get!
            #(prometheus/with-failure-timestamp gauge
               (comment 'do-something))
            gauge)]
      (= value 0.0))))

(defspec t-with-success-timestamp 25
  (prop/for-all
    [gauge gen-gauge]
    (let [{:keys [value start end]}
          (run-and-get!
            #(prometheus/with-success-timestamp gauge
               (comment 'do-something))
            gauge)]
      (<= start value end))))

(defspec t-with-success-timestamp-but-failure 25
  (prop/for-all
    [gauge gen-gauge]
    (let [{:keys [value]}
          (run-and-get!
            #(prometheus/with-success-timestamp gauge
               (throw (Exception.)))
            gauge)]
      (= value 0.0))))

(defspec t-with-timestamps-and-success 25
  (prop/for-all
    [success-gauge gen-gauge
     failure-gauge gen-gauge
     run-gauge     gen-gauge]
    (let [start (System/currentTimeMillis)
          _ (prometheus/with-timestamps
              {:last-run run-gauge
               :last-success success-gauge
               :last-failure failure-gauge}
              (comment 'do-something))
          end (System/currentTimeMillis)
          val-of #(* 1000 (prometheus/value %))]
      (and (<= start (val-of run-gauge) end)
           (<= start (val-of success-gauge) end)
           (= (val-of failure-gauge) 0.0)))))

(defspec t-with-timestamps-and-failure 25
  (prop/for-all
    [success-gauge gen-gauge
     failure-gauge gen-gauge
     run-gauge     gen-gauge]
    (let [start (System/currentTimeMillis)
          _ (try
              (prometheus/with-timestamps
                {:last-run run-gauge
                 :last-success success-gauge
                 :last-failure failure-gauge}
                (throw (Exception.)))
              (catch Throwable _))
          end (System/currentTimeMillis)
          val-of #(* 1000 (prometheus/value %))]
      (and (<= start (val-of run-gauge) end)
           (<= start (val-of failure-gauge) end)
           (= (val-of success-gauge) 0.0)))))
