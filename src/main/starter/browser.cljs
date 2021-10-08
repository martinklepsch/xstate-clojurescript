(ns starter.browser
  (:require ["xstate" :as xstate]
            ["@xstate/inspect" :as xsi]
            [applied-science.js-interop :as j]))

(def popoverseq-machine
  (xstate/createMachine
   (clj->js {:id "popoversequencer"
             :initial "idle"
             :context {:seen {}}
             :states {"idle" {:on {:DISMISS {:target "ready"
                                             :actions [:trackSeen]}}}
                      "ready" {:on {:DISMISS {:target "ready"
                                              :actions [:trackSeen]}}}}})
   ;; Important that the list of actions is provided as second arg!
   ;; Error handling for missing actions is not great
   (clj->js
    {:actions {:trackSeen (xstate/assign (fn [context event]
                                           (js/console.log :seen-popover (j/get event :popover-id))
                                           (js/console.log :context context)
                                           (j/assoc-in! context [:seen (j/get event :popover-id)] true)))}})
   ))

(def popoverseq-service
  (-> popoverseq-machine
      (xstate/interpret #js {:devTools true})
      (.onTransition (fn [state]
                       (js/console.log :transition state)))))

;; start is called by init and after code reloading finishes
(defn ^:dev/after-load start []
  (js/console.log "start")
  (.start popoverseq-service))

(defn init []
  ;; init is called ONCE when the page loads
  ;; this is called in the index.html and must be exported
  ;; so it is available even in :advanced release builds
  (js/console.log "init")
  (xsi/inspect #js {:url "https://statecharts.io/inspect"})
  (start))

;; this is called before any code is reloaded
(defn ^:dev/before-load stop []
  (js/console.log "stop"))

(comment
 (require '[shadow.cljs.devtools.api :as shadow])
 (shadow/repl :app)

 (js/console.log popoverseq-service)

 (.send popoverseq-service (clj->js {:type "DISMISS" :popover-id (str (gensym))}))

 )
