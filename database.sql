CREATE DATABASE noun_quiz;
\c noun_quiz

CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    email TEXT UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,
    points INTEGER NOT NULL DEFAULT 0,
    guessed INTEGER NOT NULL DEFAULT 0,
    missed INTEGER NOT NULL DEFAULT 0
);
