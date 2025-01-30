# Overtone-AI-Fun

Artificial Intelligent Dance Music in your REPL!

## Prerequisites

```
$ brew install clojure              # lisp 
$ brew install --cask supercollider # synth
```

## Running
In a terminal, do:

```bash
$ export ANTHROPIC_API_KEY=[your key here]
$ clj -e "(do (require 'overtone-ai-fun) (in-ns 'overtone-ai-fun))" -r
```
Wait for the repl to start: 
```
overtone-ai-fun=>
```

You can now do something like

```clj
(generate-and-run :drums)
```
or maybe 
```clj
(generate-and-run :bass :instructions "make it funky!")
```
    
Have fun!