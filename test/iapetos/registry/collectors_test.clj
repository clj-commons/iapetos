(ns iapetos.registry.collectors-test
  (:require [clojure.test.check
             [generators :as gen]
             [properties :as prop]
             [clojure-test :refer [defspec]]]
            [clojure.test :refer :all]
            [iapetos.test.generators :as g]
            [iapetos.core :as prometheus]
            [iapetos.collector :as collector]
            [iapetos.registry.collectors :as c])
  (:import [iapetos.registry IapetosRegistry]))

(defn- all-collectors
  [^IapetosRegistry registry]
  (for [[_namespace vs] (.-collectors registry)
        [_subsystem vs] vs
        [_name collector] vs]
    collector))

(defn- path-cache
  [^IapetosRegistry registry]
  (::c/path-cache (meta (.-collectors registry))))

(defspec t-registry-should-cache-the-path-of-registered-collectors 50
  (prop/for-all
   [registry              (gen/return (prometheus/collector-registry))
    collector-constructor g/collector-constructor
    collector-name        g/metric]
   (let [collector  (collector-constructor collector-name)
         registry   (prometheus/register registry collector)
         cache      (path-cache registry)]
     (is (every? (fn [{:keys [path cache-key] :as _collector}]
                   (= path (get cache cache-key)))
                 (all-collectors registry))))))

(defspec t-registry-should-evict-path-from-cache-for-unregistered-collectors 50
  (prop/for-all
   [registry              (gen/return (prometheus/collector-registry))
    collector-constructor g/collector-constructor
    collector-name        g/metric]
   (let [collector  (collector-constructor collector-name)
         registry   (-> registry
                        (prometheus/register collector)
                        (prometheus/unregister collector))]
     (is (empty? (path-cache registry))))))
