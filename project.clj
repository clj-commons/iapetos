(defproject iapetos "0.1.9-SNAPSHOT"
  :description "A Clojure Prometheus Client"
  :url "https://github.com/xsc/iapetos"
  :license {:name "MIT License"
            :url "https://opensource.org/licenses/MIT"
            :year 2016
            :key "mit"}
  :dependencies [[org.clojure/clojure "1.8.0" :scope "provided"]
                 [io.prometheus/simpleclient "0.2.0"]
                 [io.prometheus/simpleclient_common "0.2.0"]
                 [io.prometheus/simpleclient_pushgateway "0.2.0"]
                 [io.prometheus/simpleclient_hotspot "0.2.0" :scope "provided"]]
  :profiles {:dev
             {:dependencies [[org.clojure/test.check "0.9.0"]
                             [aleph "0.4.4"]]
              :global-vars {*warn-on-reflection* true}}
             :codox
             {:plugins [[lein-codox "0.10.0"]]
              :dependencies [[codox-theme-rdash "0.1.2"]]
              :codox {:project {:name "iapetos"}
                      :metadata {:doc/format :markdown}
                      :themes [:rdash]
                      :source-uri "https://github.com/xsc/iapetos/blob/v{version}/{filepath}#L{line}"
                      :namespaces [iapetos.core
                                   iapetos.export
                                   iapetos.standalone
                                   #"^iapetos\.collector\..+"]}}
             :coverage
             {:plugins [[lein-cloverage "1.0.9"]]
              :dependencies [[org.clojure/tools.reader "1.2.2"]
                             [riddley "0.1.15"]]}}
  :aliases {"codox" ["with-profile" "+codox" "codox"]
            "codecov" ["with-profile" "+coverage" "cloverage" "--codecov"]}
  :pedantic? :abort)
