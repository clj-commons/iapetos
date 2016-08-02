(ns iapetos.collector.exceptions-test
  (:require [clojure.test :refer :all]
            [clojure.test.check
             [generators :as gen]
             [properties :as prop]
             [clojure-test :refer [defspec]]]
            [iapetos.test.generators :as g]
            [iapetos.core :as prometheus]
            [iapetos.collector.exceptions :as ex]))

;; ## Generator

(def gen-exeception-counter
  (g/collector ex/exception-counter))

;; ## Tests

(defspec t-with-exceptions 100
  (prop/for-all
    [exception-counter gen-exeception-counter
     exception         (gen/elements
                         [nil
                          (Exception.)
                          (IllegalArgumentException.)
                          (IllegalStateException.)
                          (RuntimeException.)])]
    (let [exception-class (some-> exception class (.getName))
          f #(or (some-> exception throw) :ok)
          result (try
                   (ex/with-exceptions exception-counter
                     (f))
                   (catch Throwable _
                     :error))]
      (and (if exception
             (= result :error)
             (= result :ok))
           (if exception
             (= 1.0 (prometheus/value (exception-counter exception-class)))
             (= 0.0 (prometheus/value (exception-counter "java.lang.Exception"))))))))
