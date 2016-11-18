(defproject iapetos "0.1.2"
  :description "A Clojure Prometheus Client"
  :url "https://github.com/xsc/iapetos"
  :license {:name "MIT License"
            :url "https://opensource.org/licenses/MIT"
            :year 2016
            :key "mit"}
  :dependencies [[org.clojure/clojure "1.8.0" :scope "provided"]
                 [io.prometheus/simpleclient "0.0.15"]
                 [io.prometheus/simpleclient_common "0.0.15"]
                 [io.prometheus/simpleclient_pushgateway "0.0.15"]
                 [io.prometheus/simpleclient_hotspot "0.0.15" :scope "provided"]]
  :profiles {:dev
             {:dependencies [[org.clojure/test.check "0.9.0"]
                             [aleph "0.4.1"]]
              :global-vars {*warn-on-reflection* true}}
             :codox
             {:plugins [[lein-codox "0.10.0"]]
              :dependencies [[codox-theme-rdash "0.1.1"]]
              :codox {:project {:name "iapetos"}
                      :metadata {:doc/format :markdown}
                      :themes [:rdash]
                      :source-uri "https://github.com/xsc/iapetos/blob/v{version}/{filepath}#L{line}"
                      :namespaces [iapetos.core
                                   iapetos.export
                                   iapetos.standalone
                                   #"^iapetos\.collector\..+"]}}}
  :aliases {"codox" ["with-profile" "+codox" "codox"]}
  :pedantic? :abort)
