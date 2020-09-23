# Интеграционные тесты для ratelimit оператора

## Тестовы сценарии

1. Корректное создание RateLimiter и RateLimiterConfig `createRateLimiter`

    Создаем:
    - RateLimiter
    - RateLimiterConfig
    
    Редактируем:
    - RateLimiter
    - RateLimiterConfig
    
    Проверяем:
    - RateLimiter и RateLimiterConfig созданы успешно 
    - созданы дочерние ресурсы по описанию RateLimiter: Deployment (ratelimiter, redis), Service (ratelimiter, redis), ConfigMap
    - созданы дочерние ресурсы по описанию RateLimiterConfig: EnvoyFilter, файл в ConfigMap

2. Редактирование RateLimiterConfig `editAllFieldsRateLimiterConfig`

    Создаем:
    - RateLimiter
    - RateLimiterConfig

    Редактируем:
    - RateLimiterConfig
    
    Проверяем:
    - обновление EnvoyFilter и соответствующего файла в ConfigMap согласно внесенным изменениям

3. Удаление дочерних ресурсов `deleteResources`

    Создаем:
    - RateLimiter
    - RateLimiterConfig

    Удаляем:
    - дочерние ресурсы для RateLimiter (Deployment (ratelimiter, redis), Service (ratelimiter, redis), ConfigMap)
    - дочерние ресурсы для RateLimiterConfig (EnvoyFilter)
    
    Проверяем:
    - восстановлены дочерние ресурсы RateLimiter
    - восстановлены дочерние ресурсы RateLimiterConfig

4. Изменение дочернего EnvoyFilter `editEnvoyFilter`

    Создаем:
    - RateLimiter
    - RateLimiterConfig

    Редактируем:
    - EnvoyFilter
    
    Проверяем: 
    - изменения в EnvoyFilter откатываются к первоначальным (до редактирования)

5. Изменение дочерних Service'ов `editService`

    Создаем:
    - RateLimiter
    - RateLimiterConfig

    Редактируем:
    - Service (ratelimiter, redis)
    
    Проверяем:
    - изменения в Service (ratelimiter, redis) откатываются к первоначальным (до редактирования)

6. Изменение дочерних Deployment'ов `editDeployment`

    Создаем:
    - RateLimiter
    - RateLimiterConfig

    Редактируем: 
    - Deployment (ratelimiter, redis)
    
    Проверяем:
    - изменения в Deployment (ratelimiter, redis) откатываются к первоначальным (до редактирования)

7. Изменение дочерней ConfigMap `editConfigMap`

    Создаем:
    - RateLimiter
    - RateLimiterConfig

    Редактируем:
    - ConfigMap
    
    Проверяем:
    - изменения в ConfigMap откатываются к первоначальным (до редактирования)
    
8. Пересоздание RateLimiter `recreateRateLimiterAndCheckConfigMap`

    Создаем:
    - RateLimiter
    - RateLimiterConfig
    
    Удаляем и снова создаем:
    - RateLimiter
    
    Проверяем:
    - ConfigMap создалась заново
    - в ConfigMap присутствует файл для RateLimiterConfig

9. Уникальные домены `sameDomainsRateLimiterConfigs`

    Создаем:
    - RateLimiter
    - несколько ресурсов RateLimiterConfig
    
    Проверяем:
    - все домены в файлах ConfigMap уникальны

10. Удаление workloadSelector в RateLimiterConfig `createRateLimiterWithOutWorkLoadSelector`

    Создаем:
    - RateLimiter
    - RateLimiterConfig

    Удаляем и снова создаем:
    - workloadSelector в RateLimiterConfig
    
    Проверяем:
    - в EnvoyFilter изменяется workloadSelector
    
11. Удаляем RateLimiter и RateLimiterConfig `deleteRateLimiterConfigAfterRateLimiter`  

    Создаем:
    - RateLimiter
    - RateLimiterConfig  

    Удаляем:
    - RateLimiter
    - RateLimiterConfig
    
    Проверяем:
    - удалены дочерние ресурсы для RateLimiter 
    - удалены дочерние ресурсы для RateLimiterConfig 
 
12. Создаем RateLimiterConfig без RateLimiter `createRateLimiterConfigWithOutRateLimiter`

    Создаем:
    - RateLimiterConfig (RateLimiter нет)
    
    Проверяем:
    - webhook выбрасывает ошибку при создании RateLimiterConfig
