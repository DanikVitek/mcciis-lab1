ages = 50; % Кількість поколінь мурах
ants = 60; % Кількість мурах в одному поколінні
n = 60; % Кількість міст
a = 1; % Кількість феромону на шляху (коефіцієнт значимості шляху)
b = 3; % Пріоритет відстані над кількістю феромону
p = 0.5; % Кількість феромону на шляху, що лишає агент
Q = 1; % Кількість феромону, що лежить на шляху
pheromones_0 = 0.01; % Початкова кількість феромону
distance = zeros(n, n); % Матриця відстаней
reverse = zeros(n, n); % Матриця зворотніх відстаней
routes_1 = zeros(ants, n); % Матриця маршрутів в одному поколінні
distances_1 = zeros(ants, 1); % Вектор відстаней одного покоління мурах
best_distance = inf; % Стартове значення найкращої дистанції
opt_routes = zeros(1, n); % Оптимальні шляхи
city_mix = randperm(n); % Перемішування списку міст
priority = zeros(1, n); % Матриця ймовірностей переходу між містами
cities = rand(n, 2) * 100; % Генеруємо координати міст
pher_on_routes = pheromones_0 * (ones(n, n)); % Матриця початкової кількості феромонів
% Формуємо матриці відстаней в обидва боки
for i = 1:n
    for j = 1:n
        % Відстань вперед
        distance(i,j) = sqrt((cities(i, 1) - cities(j, 1)) ^ 2 + (cities(i, 2) - cities(j, 2)) ^ 2);
        if i ~= j
            % відстань назад
            reverse(i, j) = 1 / sqrt((cities(i, 1) - cities(j, 1)) ^ 2 + (cities(i, 2) - cities(j, 2)) ^ 2);
        end
    end
end
for ii = 1:ages % Кожне покоління
    for k = 1:ants % Кожна мураха з покоління
        routes_1(k, 1) = city_mix(k); % Розташовуємо кожну мураху в окреме місто
        for s = 2:n % Шлях наступних мурах (після першої)
            city_id = routes_1(k, s-1); % Індекс міста
            % Обраховуємо ймовірність відвідування міст

            priority = pher_on_routes(city_id,:).^a .* reverse(city_id,:).^b;
            priority(routes_1(k, 1:s-1)) = 0; % Враховуємо міста, в яких мурахи вже були
            priority = priority ./ sum(priority); % Обраховуємо кінцеву ймовірність переходів
            next_city = find(cumsum(priority) >= rand, 1, 'first'); % Обрахунок наступного переходу
            routes_1(k,s) = next_city; % Закріплюємо місто за мурахою
        end
        opt_routes = [routes_1(k,1:end), routes_1(k,1)]; % Обраховуэмо маршрут мурахи
        S = 0; % Довжина шляху
        for i = 1:n % Обраховуэмо довжину маршруту мурахи
            S = S + distance(opt_routes(i), opt_routes(i+1));
        end
        distances_1(k) = S; % Загальний шлях мурах в поколінні
        if distances_1(k) < best_distance % Перевіряємо результати нового покоління і найкращого попереднього
            best_distance = distances_1(k); % Міняємо значення найкращого шляху
            best_route = routes_1(k, 1:end); % Міняємо значення найкращого маршруту
            cities_final(:, [1, 2]) = cities(best_route(:), [1, 2]); % Формуємо маршрут
            % Будуємо графік маршрутів

            plot([cities_final(:, 1); cities_final(1, 1)], [cities_final(:, 2); cities_final(1, 2)], 'k-o');

            % Заголовки для графіка
            title({['iteration ', num2str(ii)]; ['ant ', num2str(k)]; ['best route ',
            num2str(distances_1(k))]});
            pause(0.1) % Час оновлення графіку
        end
    end
    pher_on_routes = (1 - p) * pher_on_routes; % Оновлюємо кількість феромону
    for u = 1:ants % Кожна мураха
        opt_routes = [routes_1(u, 1:end), routes_1(u, 1)]; % Шлях кожної мурахи
        for t = 1:n % Кожне місто
            x = opt_routes(t); % Шлях до поточного міста
            y = opt_routes(t + 1); % Шлях до наступного міста
            pher_on_routes(x, y) = pher_on_routes(x, y) + Q / distances_1(u); % Добавляємо феромони на шляхи
        end
    end
end
disp(best_distance); % Виводимо на екран найкращу дистанцію
clearvars -except cities; % Очищуємо всі змінні окрім карти міст