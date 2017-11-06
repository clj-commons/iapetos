(ns iapetos.registry-test
  (:require [clojure.test.check
             [generators :as gen]
             [properties :as prop]
             [clojure-test :refer [defspec]]]
            [clojure.test :refer :all]
            [iapetos.test.generators :as g]
            [iapetos.core :as prometheus]
            [iapetos.collector :as c]
            [iapetos.export :as export]))

(defspec t-registry-should-return-nil-for-unknown-collectors 50
  (prop/for-all
    [registry-fn           (g/registry-fn)
     collector-name        g/metric]
    (let [registry (registry-fn)]
      (nil? (registry collector-name)))))

(defspec t-registry-should-return-a-registered-collector 50
  (prop/for-all
    [registry-fn           (g/registry-fn)
     collector-constructor g/collector-constructor
     collector-name        g/metric]
    (let [collector (collector-constructor collector-name)
          registry (-> (registry-fn)
                       (prometheus/register collector))]
      (some? (registry collector-name)))))

(defspec t-registry-should-return-a-registered-collector-with-explicit-name 50
  (prop/for-all
    [registry-fn           (g/registry-fn)
     collector-constructor g/collector-constructor
     collector-name        g/metric
     register-name         g/metric]
    (let [collector (collector-constructor collector-name)
          registry (-> (registry-fn)
                       (prometheus/register-as register-name collector))]
      (some? (registry register-name)))))

(defspec t-registry-should-return-nil-for-unregistered-collectors 50
  (prop/for-all
    [registry-fn           (g/registry-fn)
     collector-constructor g/collector-constructor
     collector-name        g/metric]
    (let [collector (collector-constructor collector-name)
          registry (-> (registry-fn)
                       (prometheus/register collector)
                       (prometheus/unregister collector-name))]
      (nil? (registry collector-name)))))

(defspec t-registry-should-clear-all-collectors 50
  (prop/for-all
    [registry-fn           (g/registry-fn)
     collectors            (gen/not-empty g/collectors)]
    (let [registry (apply prometheus/register (registry-fn) collectors)]
      (and (is (not= "" (export/text-format registry)))
           (let [cleared-registry (prometheus/clear registry)]
             (is (= "" (export/text-format cleared-registry))))))))

(defspec t-subsystem=registry-should-only-clear-own-collectors 50
  (prop/for-all
    [registry-fn   (g/registry-fn)
     collectors    g/collectors]
    (let [[h & rst] collectors
          registry (-> (registry-fn)
                       (cond-> h (prometheus/register h)))
          export-before (export/text-format registry)
          subregistry (apply prometheus/register
                             (prometheus/subsystem registry "sub")
                             (rest collectors))
          export-with-sub (export/text-format registry)
          subregistry' (prometheus/clear subregistry)
          export-after (export/text-format registry)]
      (and (= export-before export-after)
           (or (not (seq rst))
               (not= export-with-sub export-before))))))
