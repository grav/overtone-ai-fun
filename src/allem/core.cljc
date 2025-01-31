(ns allem.core
  (:require [org.httpkit.client :as client]
            [clojure.data.json :as json]
            [allem.util :as util]))

(defn remove-think [s]
  (if-let [[_ matches] (re-matches #"<think>[\s\S]*?</think>([\s\S]*)" s)]
    matches
    s))

(defn throw-on-error [{:keys [status body]
                       :as response}]
  (if (>= status 400)
    (throw (ex-info (str "HTTP Error: " status "\n" body)
                    {:status status
                     :body   body
                     :type   :http-error}))
    response))

(defn request-with-throw-on-error [& args]
  (-> @(apply client/request args)
      throw-on-error))


(defn groq [{:keys [msg model]
             :or {model "deepseek-r1-distill-llama-70b"}}]
  (let [body {:model      model
              :max_tokens 1024
              :messages   [{:role "user"
                            :content msg}]}]
    (-> @(client/request
           {:headers {"Authorization"     (format "Bearer %s" (System/getenv "GROQ_API_KEY"))
                      "content-type"      "application/json"}
            :method  :post
            :url     "https://api.groq.com/openai/v1/chat/completions"
            :body    (json/json-str body)})
        :body
        (json/read-str :key-fn keyword)
        :choices
        util/single
        :message
        :content
        remove-think
        #_util/tap->)))

;;; https://docs.anthropic.com/en/api/messages
(defn claude [{:keys [msg model]
               :or {model "claude-3-5-sonnet-20241022"}}]
  (let [body {:model      model
              :max_tokens 1024
              :messages   [{:role "user"
                            :content msg}]}]


    (-> (request-with-throw-on-error
          {:headers {"x-api-key"         (System/getenv "ANTHROPIC_API_KEY")
                     "anthropic-version" "2023-06-01"
                     "content-type"      "application/json"}
           :method  :post
           :url     "https://api.anthropic.com/v1/messages"
           :body    (json/json-str body)})
        :body
        (json/read-str :key-fn keyword)
        :content
        util/single
        #_util/tap->
        :text)))

(defn together-ai [{:keys [msg model]
                    :or {model "deepseek-ai/DeepSeek-V3"}}]
  (let [body {:model      model
              :max_tokens 1024
              :messages   [{:role "user"
                            :content msg}]}]
    (-> @(client/request
           {:headers {"Authorization"     (format "Bearer %s" (System/getenv "TOGETHERAI_API_KEY"))
                      "content-type"      "application/json"}
            :method  :post
            :url     "https://api.together.xyz/v1/chat/completions"
            :body    (json/json-str body)})
        :body
        (json/read-str :key-fn keyword)
        :choices
        util/single
        :message
        :content
        #_util/tap->)))

(defn ollama [{:keys [msg model]
               :or {model "mistral-small:24b"}}]
  (-> (request-with-throw-on-error
        {:headers
         {"content-type" "application/json"}
         :method :post
         :url "http://localhost:11434/api/generate"
         :body (json/json-str
                 {:model model
                  :prompt msg
                  :stream false})})
      :body
      (json/read-str :key-fn keyword)
      :response))

(comment
  (ollama {:msg "why is the sky blue?"}))