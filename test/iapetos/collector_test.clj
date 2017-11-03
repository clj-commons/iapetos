(ns iapetos.collector-test
  (:require [clojure.test.check
             [generators :as gen]
             [properties :as prop]
             [clojure-test :refer [defspec]]]
            [clojure.test :refer :all]
            [iapetos.test.generators :as g]
            [iapetos.collector :as c])
  (:import [io.prometheus.client
            Counter
            Histogram
            Gauge
            SimpleCollector$Builder
            Summary]))

(def gen-raw-collector
  (gen/let [builder (gen/elements
                      [(Counter/build)
                       (Histogram/build)
                       (Gauge/build)
                       (Summary/build)])
            collector-namespace g/metric-string
            collector-name g/metric-string
            help-string (gen/not-empty gen/string-ascii)]
    (gen/return
      {:collector           (-> ^SimpleCollector$Builder
                                 builder
                                 (.name collector-name)
                                 (.namespace collector-namespace)
                                 (.help help-string)
                                 (.create))
       :collector-namespace collector-namespace
       :collector-name      collector-name})))

(defspec t-raw-collectors 20
  (prop/for-all
    [{:keys [collector collector-name collector-namespace]}
     gen-raw-collector]
    (and (is (= collector
                (c/instantiate collector {})))
         (is (= {:name      collector-name
                 :namespace collector-namespace}
                (c/metric collector)))
         (is (= collector
                (c/label-instance collector collector {}))))))
