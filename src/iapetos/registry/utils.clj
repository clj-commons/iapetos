(ns iapetos.registry.utils
  (:require [iapetos.metric :as metric]))

(defn join-subsystem
  [old-subsystem subsystem]
  (if (seq old-subsystem)
    (metric/sanitize (str old-subsystem "_" subsystem))
    subsystem))

(defn metric->path
  [metric {:keys [subsystem]}]
  (let [{:keys [name namespace]} (metric/metric-name metric)]
    [namespace subsystem name]))
