(ns iapetos.collector.jvm-test
  (:require [clojure.test.check
             [generators :as gen]
             [properties :as prop]
             [clojure-test :refer [defspec]]]
            [clojure.test :refer :all]
            [iapetos.test.generators :as g]
            [iapetos.core :as prometheus]
            [iapetos.collector.jvm :as jvm]))

(defspec t-jvm-collectors 10
  (prop/for-all
    [registry-fn (g/registry-fn)]
    (let [registry (-> (registry-fn)
                       (jvm/initialize))]
      (and (registry :iapetos-internal/jvm-standard)
           (registry :iapetos-internal/jvm-gc)
           (registry :iapetos-internal/jvm-memory-pools)
           (registry :iapetos-internal/jvm-threads)
           (registry :iapetos-internal/jvm-buffer-pools)
           (registry :iapetos-internal/jvm-class-loading)
           (registry :iapetos-internal/jvm-version-info)))))
