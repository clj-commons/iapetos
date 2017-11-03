(ns iapetos.core.lazy-test
  (:require [clojure.test.check
             [generators :as gen]
             [properties :as prop]
             [clojure-test :refer [defspec]]]
            [clojure.test :refer :all]
            [iapetos.test.generators :as g]
            [iapetos.core :as prometheus]
            [iapetos.export :as export]))

(def gen-collector-constructor
  (gen/elements
    [prometheus/gauge
     prometheus/counter
     prometheus/summary]))

(defspec t-lazy-deprecation 20
  (prop/for-all
    [registry-fn    (g/registry-fn)
     collector-fn   g/collector-constructor
     collector-name g/metric]
    (let [collector (collector-fn collector-name {:lazy? true})
          registry  (registry-fn)
          output    (with-out-str
                      (prometheus/register registry collector))]
      (.startsWith
        ^String output
        "collector option ':lazy?' is deprecated, use 'register-lazy' instead."))))

(defspec t-register-lazy 100
  (prop/for-all
    [registry-fn    (g/registry-fn)
     collector-fn   g/collector-constructor
     collector-name g/metric]
    (let [collector (collector-fn collector-name)
          registry  (-> (registry-fn)
                        (prometheus/register-lazy collector))]
      (and (is (= "" (export/text-format registry)))
           (is (registry collector-name))
           (is (not= "" (export/text-format registry)))))))
