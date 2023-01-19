(ns iapetos.metric-test
  (:require [clojure.test :refer :all]
            [clojure.test.check
             [generators :as gen]
             [properties :as prop]
             [clojure-test :refer [defspec]]]
            [iapetos.test.generators :as g]
            [iapetos.metric :as metric]))

(defspec t-dasherize 100
  (prop/for-all
    [s g/metric-string]
    (let [result (metric/dasherize s)]
      (and (<= (count result) (count s))
           (re-matches #"[a-zA-Z0-9\-]*" result)))))

(defspec t-sanitize 100
  (prop/for-all
    [s g/metric-string]
    (let [result (metric/sanitize s)]
      (and (<= (count result) (count s))
           (re-matches #"[a-zA-Z0-9_]*" result)))))

(defspec t-sanitize-exception 100
  (prop/for-all
    [s g/invalid-metric-string]
    (= (try
         (metric/sanitize s)
         (catch AssertionError e
           :error))
       :error)))

(defspec t-metric-name 100
  (prop/for-all
    [metric g/metric]
    (let [{:keys [name namespace]} (metric/metric-name metric)]
      (and (re-matches #"[a-zA-Z0-9_]+" name)
           (re-matches #"[a-zA-Z0-9_]+" namespace)))))

(defspec t-metric-as-map 100
  (prop/for-all
    [metric          g/metric
     additional-keys (gen/map gen/string-ascii gen/string-ascii)]
    (let [{:keys [name namespace] :as r} (metric/as-map metric additional-keys)]
      (and (re-matches #"[a-zA-Z0-9_]+" name)
           (re-matches #"[a-zA-Z0-9_]+" namespace)
           (= additional-keys (dissoc r :name :namespace ::metric/id))
           (= metric (::metric/id r))))))
