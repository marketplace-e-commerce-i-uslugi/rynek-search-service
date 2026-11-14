-- Historia wyszukiwań zalogowanego użytkownika: jedno zapytanie raz (ponowne wyszukanie odświeża czas), query_key to zapytanie
-- bez wielkości liter i polskich znaków. Serwis trzyma najwyżej 20 ostatnich na użytkownika.
CREATE TABLE search_history (
    id          uuid PRIMARY KEY,
    user_id     uuid         NOT NULL,
    query       varchar(100) NOT NULL,
    query_key   varchar(100) NOT NULL,
    searched_at timestamptz  NOT NULL,
    UNIQUE (user_id, query_key)
);

CREATE INDEX search_history_user_idx ON search_history (user_id, searched_at DESC);

-- Zapisane wyszukiwania (filtry jako JSON). notify: użytkownik chce powiadomień o nowych ofertach (wysyłka jeszcze nie działa).
CREATE TABLE saved_searches (
    id         uuid PRIMARY KEY,
    user_id    uuid        NOT NULL,
    label      varchar(60) NOT NULL,
    filters    text        NOT NULL,
    notify     boolean     NOT NULL,
    created_at timestamptz NOT NULL
);

CREATE INDEX saved_searches_user_idx ON saved_searches (user_id, created_at DESC);
