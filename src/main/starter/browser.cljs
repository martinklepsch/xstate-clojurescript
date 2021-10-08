(ns starter.browser
  (:require ["xstate" :as xstate]
            ["@xstate/inspect" :as xsi]
            [starter.xstate :as xs]
            [applied-science.js-interop :as j]))

;; API via React Context
;; (use-popover? :my_popover_id) => true or false
;; on mount this sends a REQUEST_POPOVER event
;; on unmount UNREQUEST_POPOVER
;; hook returns true if popover should be shown

;; Statechart could live in react context or global application state just exporting a single "show this" via React context

;; Whats missing?
;; - Updates to seen that occur from different client / outside the state machine
;; - Could be extended to allow a "force show" that puts showing items back on queue

(def counter-machine
  (xstate/createMachine
   #js {:id "counter-machine"
        :initial "start"
        :context (xs/context {:some {:count 0}})
        :states (clj->js {"start" {:on {:INC {:actions [:increment!]}
                                        :DEC {:actions [:decrement!]}}}})}
   (clj->js {:actions {:decrement! (xs/assign (fn [context event]
                                                (js/console.log :dec-event (pr-str event))
                                                (update-in context [:some :count] dec)))
                       :increment! (xs/assign (fn [context event]
                                                (js/console.log :inc-event (pr-str event))
                                                (update-in context [:some :count] inc)))}})))

;; The first action sucessfully updates the initial context, a second update
;; then receives a context that has been "meddled with", i.e. it no longer is
;; compatible with the various Clojure functions that would usually operate on
;; maps.


(def popoverseq-machine
  (xstate/createMachine
   (clj->js {:id "popoversequencer"
             :initial "idle"
             :context {:seen {}
                       :showing nil
                       :queue []}
             ;; order of actions:
             ;; (1) exit of current state (2) actions of transition (3) entry of next state
             :states {"idle" {:on {:INITIALIZE {:target "ready"
                                                :actions [:setSeen]}
                                   :REQUEST_POPOVER {:cond :should-queue?
                                                     :actions [:queue-popover!]}}}
                      "ready" {:entry [{:cond :popover-available?
                                        :actions (xstate/send "SHOW_POPOVER")}]
                               #_(xstate/send "MAYBE_SHOW_POPOVER")

                               :on {:REQUEST_POPOVER [{:cond :should-queue?
                                                       :actions [:queue-popover! (xstate/send "SHOW_POPOVER")]}]
                                    :MAYBE_SHOW_POPOVER [{:cond :popover-available?
                                                          :actions (xstate/send "SHOW_POPOVER")}]
                                    :SHOW_POPOVER [{:target "showing-popover"
                                                    :actions [:show-popover!]}]}}

                      "showing-popover" {:on {:DISMISS [{:cond :queue-empty?
                                                         :target "ready"
                                                         :actions [:dismiss-popover! :unset-showing!]}
                                                        {:cond :popover-available?
                                                         :actions [:dismiss-popover! :show-popover!]}]
                                              :REQUEST_POPOVER {:cond :should-queue?
                                                                :actions [:queue-popover!]}
                                              ;; If unrequesting current popover, hide without dismiss
                                              ;; If unrequesting popover in queue, remove from queue
                                              :UNREQUEST_POPOVER [{:cond :currently-showing?
                                                                   :target "ready"
                                                                   :actions [:unset-showing!]}
                                                                  {:actions [:unqueue-popover!]}]}}
                      }})
   ;; Important that the list of actions is provided as second arg!
   ;; Error handling for missing actions is not great
   (clj->js
    {:guards {:queue-empty? (fn [context _event]
                              (nil? (first (j/get context :queue))))
              :should-queue? (fn [context event]
                               (and (not= (j/get context :showing) (j/get event :popover-id)) ;not showing
                                    (not (contains? (js->clj (j/get context :seen)) (j/get event :popover-id))) ;not seen
                                    (not (contains? (set (j/get context :queue)) (j/get event :popover-id))))) ;not in queue
              :popover-available? (fn [context _event]
                                    (boolean (first (j/get context :queue))))
              :currently-showing? (fn [context event]
                                    (= (j/get context :showing) (j/get event :popover-id)))}
     :actions {:unset-showing! (xstate/assign (fn [context _event]
                                                (j/assoc! context :showing nil)))
               :show-popover! (xstate/assign (fn [context _event]
                                               (-> context
                                                   ;; TODO insert fancy logic to pick the most important popover from the queue
                                                   (j/assoc! :showing (first (j/get context :queue)))
                                                   (j/assoc! :queue (clj->js (rest (j/get context :queue)))))))
               :dismiss-popover! (xstate/assign (fn [context _event]
                                                  (let [seen (j/get context :showing)]
                                                    (assert seen)
                                                    (j/assoc-in! context [:seen seen] true))))
               :unqueue-popover! (xstate/assign (fn [context event]
                                                  (j/assoc! context :queue
                                                            (clj->js (remove #{(j/get event :popover-id)} (j/get context :queue))))))
               :queue-popover! (xstate/assign (fn [context event]
                                                (let [{:keys [popover-id]} (j/lookup event)]
                                                  (j/update! context :queue j/push! popover-id))))
               :initialize-seen! (xstate/assign (fn [context _event]
                                                  ;; TODO should read from remote data source
                                                  (j/assoc-in! context [:seen "foobar"] true)))}})))

(defonce popoverseq-service
  (-> counter-machine
      (xstate/interpret #js {:devTools true})
      (.onTransition (fn [state]
                       (js/console.log :transition (pr-str (j/get-in state [:context xs/context-key])))))))

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

 (.send popoverseq-service #js {:type "INC" :some "data"})
 (.send popoverseq-service #js {:type "DEC" :foo "bar"})

 (.send popoverseq-service (clj->js {:type "DISMISS"}))
 (.send popoverseq-service (clj->js {:type "INITIALIZE"}))

 (.send popoverseq-service (clj->js {:type "REQUEST_POPOVER" :popover-id "1"}))
 (.send popoverseq-service (clj->js {:type "REQUEST_POPOVER" :popover-id "2"}))
 (.send popoverseq-service (clj->js {:type "REQUEST_POPOVER" :popover-id "3"}))

 (.send popoverseq-service (clj->js {:type "UNREQUEST_POPOVER" :popover-id "1"}))
 (.send popoverseq-service (clj->js {:type "UNREQUEST_POPOVER" :popover-id "2"}))
 (.send popoverseq-service (clj->js {:type "UNREQUEST_POPOVER" :popover-id "3"}))

 )
