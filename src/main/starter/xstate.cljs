(ns starter.xstate
  (:require ["xstate" :as xstate]
            [cljs-bean.core :as cljs-bean]
            [applied-science.js-interop :as j]))

(defn context [initial]
  #js {"__cljs_context" initial})

(def context-key
  "__cljs_context")

(defn get-context [context]
  (j/get context context-key))

(defn assign [updater]
  (xstate/assign
   (fn [context event]
     (j/assoc! context
               context-key
               (updater (get-context context) (cljs-bean/->clj event))))))

(defn ev
  ([ev-type]
   (ev ev-type nil))
  ([ev-type payload]
   (assert (string? ev-type))
   #js {:type ev-type :data (assoc payload :type ev-type)}))

