# noun-quiz

A little game: type a proverb by looking at icons (fetched from The Noun Project) that represent words.

Inspired by `clojurebreaker`.

## Prerequisites

You will need [Leiningen][] 2.0.0 or above installed.

[leiningen]: https://github.com/technomancy/leiningen

## Running

Consult the `resources/config.edn` file for configuration options. You can override (`merge`) options without editing the file by setting the `NOUN_QUIZ_CONFIG` environment variable.

First option you need to change is The Noun Project API key. The second option is database connection info. You can use `database.sql` file to initialize a PostgreSQL database.

To start a web server for the application, run:

    lein ring server

Or you can try it on Heroku:

![game screenshot](https://raw.githubusercontent.com/PawelStroinski/noun-quiz/master/screenshot.png)

## License

Copyright © 2015 Paweł Stroiński
