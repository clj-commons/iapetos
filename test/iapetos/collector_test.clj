(ns iapetos.collector-test
  (:require [clojure.test.check
             [generators :as gen]
             [properties :as prop]
             [clojure-test :refer [defspec]]]
            [clojure.test :refer :all]
            [iapetos.test.generators :as g]
            [iapetos.collector :as c])
  (:import [io.prometheus.metrics.core.metrics
            Counter
            Histogram
            Gauge
            MetricWithFixedMetadata$Builder
            Summary]))

(def gen-raw-collector
  (gen/let [builder (gen/elements
                      [(Counter/builder)
                       (Histogram/builder)
                       (Gauge/builder)
                       (Summary/builder)])
            collector-namespace g/metric-string
            collector-name g/metric-string
            help-string (gen/not-empty gen/string-ascii)]
    (gen/return
      {:collector           (-> ^MetricWithFixedMetadata$Builder
                                builder
                                (.name (str collector-namespace "_" collector-name))
                                (.help help-string)
                                (.build))
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
         (is (thrown?
               UnsupportedOperationException
               (c/label-instance collector collector {:label "value"})))
         (is (= collector
                (c/label-instance collector collector {}))))))
