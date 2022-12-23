require: slotfilling/slotFilling.sc
  module = sys.zb-common

require: localPatterns.sc

require: dicts/cities.csv
    name = cities
    var = cities

require: dicts/countries.csv
    name = countries
    var = countries

require: dicts/names.csv
    name = names
    var = names
    
require: functions.js

init:
    bind("postProcess", function($context) {
        $context.session.lastState = $context.currentState;
    });

init:
    $global.$ = {
        __noSuchProperty__: function(property) {
            return $jsapi.context()[property];
        }
    };



theme: /
    state: Welcome
        q!: *start
        q!: $hi *
        script:
            $session = {};
            $response.replies = $response.replies || [];
            $response.replies.push( {
                type:"image",
                "imageUrl":"https://447906.selcdn.ru/Tver/justtour.jpg"
            });
        a: Меня зовут {{ $injector.botName }}, я бот туристической компании «Just Tour» .
        
        if: $client.name
            go!: /Name/ConfirmName
        else:
            go!: /Name/AskName


    state: CatchAll || noContext = true
        event!: noMatch
        a: Простите, я не поняла. Попробуйте написать по другому.


theme: /Name
    state: AskName || modal = true
        a: Как я могу к Вам обращаться ?

        state: GetName
            q: * $Name *
            script:
                $client.name = $parseTree._Name.name;
            go!: /Service/SuggestHelp

        state: GetStrangeName
            q: * 
            a: Я знаю больше двух тысяч разных имен, но такое вижу в первые. Вас точно так зовут?
            script:
                $session.probablyName = $request.query;
            
            buttons:
                "Да"
                "Нет"
            
            state: Yes
                q: (да/верно)
                script:
                    $client.name = $session.probablyName;
                    delete $session.probablyName;
                go!: /Service/SuggestHelp

            state: No
                q: (нет/не [верно])
                go!: /Name/AskName
        
        state: NoName
            q: * (не скажу/не хочу/не буду/[а тебе] [не] зачем/еще чего/фиг/фигушки/нет/никак/[как] хочешь) *
            a: Ваше Имя нужно для оформления заявки,иначе я не смогу Вам помочь подобрать тур, могу только рассказать о погоде.    
            

    state: ConfirmName
        a: Ваше имя {{ $client.name }}, верно?
        script:
            $session.probablyName = $client.name;
        buttons:
            "Да"
            "Нет"

        state: Yes
            q: (да/верно)
            script:
                $client.name = $session.probablyName;
                delete $session.probablyName;
            go!: /Service/SuggestHelp

        state: No
            q: (нет/не [верно])
            go!: /Name/AskName


#============================================= Базовый запрос на погоду и путевку =============================================#

theme: /Service
    state: SuggestHelp
        a: {{ $client.name }}, Я могу расказать Вам о текущей погоде в любом городе или стране, а так же подобрать туристическую путевку. 
        a: Что Вас интересует?
        
        buttons:
            "Путевка"
            "Погода"

#============================================= ПОГОДА =============================================#

theme: /Weather 
    
    state: WeatherQust 
        #вопрос из любого места о погоде без конкретики. Если до этого погоду уже спрашивали, то уточнит по месту. 
        q!: * (~погода) *
        q!: * [хочу] узнать погоду *
        q!: * {погоду подскажи} *
        q!: * {(погодочку/погодку/погоду) [бы]}
        if: $session.arrivalPointForCity
            a: Вы хотели бы узнать погоду в {{ $session.arrivalPointForCity }}?
        elseif: $session.arrivalPointForCountry
            a: Вы хотели бы узнать погоду в {{ $session.arrivalPointForCountry }}?
        else:
            a: В Каком Городе/Стране Вас интересует погода?
        
        state: WeatherYes
            q: * $comYes *
            q: верно
            q: * [это] (он/оно/то что нужно) *
            if: $session.arrivalPointForCity
                go!: /Weather/WeatherOfCity
            elseif: $session.arrivalPointForCountry
                go!: /Weather/WeatherOfCountry
            
        state: WeatherNO
            q: * $comNo *
            q: * (не верно/неверно) *
            q: * [это] не то [что я хотел] *
            script:
                delete $session.arrivalPointForCity;
                delete $session.arrivalPointForCountry;
                delete $session.arrivalCoordinates;
            go!: /Weather/WeatherQust
        
        state: WeatherCity
            q: * $City *
            go!: /Weather/WeatherInTheCity
        
        state: WeatherCountry
            q: * $Country *
            go!: /Weather/WeatherInTheCountry
    
    
    
    state: WeatherInTheCity
        #вопрос из любого места о погоде в конкретном городе
        q!: * [$Question] * $Weather * $City *
        q!: * [$Question] * $City * $Weather *
        q!: * а в $City *
        script:
            $session.arrivalPointForCity = $parseTree._City.name;
            $session.arrivalCoordinates = {
                lat: $parseTree._City.lat,
                lon: $parseTree._City.lon
            };
            $temp.Weather = getWeather($session.arrivalCoordinates.lat, $session.arrivalCoordinates.lon);
            $session.TempForQuest = $temp.Weather.temp;
        if: $temp.Weather
            a: В городе {{ $session.arrivalPointForCity }} сейчас {{ $temp.Weather.description }} {{ $temp.Weather.temp }}°C. Ощущается как {{ $temp.Weather.feelslike }}°C.
            go!: /Weather/AreYouSure

    
    state: WeatherInTheCountry 
        #вопрос из любого места о погоде в конкретной стране
        q!: * [$Question] * $Weather * $Country *
        q!: * [$Question] * $Country * $Weather *
        q!: * а в $Country *
        script:
            $session.arrivalPointForCountry = $parseTree._Country.namesc;
            $session.arrivalCoordinates = {
                lat: $parseTree._Country.lat,
                lon: $parseTree._Country.lon
            };
            $temp.Weather = getWeather($session.arrivalCoordinates.lat, $session.arrivalCoordinates.lon);
            $session.TempForQuest = $temp.Weather.temp;
        if: $temp.Weather
            a: В {{ $session.arrivalPointForCountry }} сейчас {{ $temp.Weather.description }} {{ $temp.Weather.temp }}°C. Ощущается как {{ $temp.Weather.feelslike }}°C.
            go!: /Weather/AreYouSure
    

    state: WeatherOfCity 
        #для варианта когда уже был известен город и сессионая переменная уже обозначена
        script:
            $temp.Weather = getWeather($session.arrivalCoordinates.lat, $session.arrivalCoordinates.lon);
            $session.TempForQuest = $temp.Weather.temp;
        if: $temp.Weather
            a: В городе {{ $session.arrivalPointForCity }} сейчас {{ $temp.Weather.description }} {{ $temp.Weather.temp }}°C. Ощущается как {{ $temp.Weather.feelslike }}°C.
            go!: /Weather/AreYouSure
        
            
    state: WeatherOfCountry 
        #для варианта когда уже была известна страна и сессионая переменная уже обозначена
        script:
            $temp.Weather = getWeather($session.arrivalCoordinates.lat, $session.arrivalCoordinates.lon);
            $session.TempForQuest = $temp.Weather.temp;
        if: $temp.Weather
            a: В {{ $session.arrivalPointForCountry }} сейчас {{ $temp.Weather.description }} {{ $temp.Weather.temp }}°C. Ощущается как {{ $temp.Weather.feelslike }}°C.
            go!: /Weather/AreYouSure
        
        
    state: AreYouSure
        #уточнение точно клиент хочет туда поехать?
        script:
            if ($session.TempForQuest < 25 && $session.TempForQuest > 0) {
                $reactions.answer("Вы хотели бы запланировать поездку в страну с умеренным климатом?");
            }
            else if ($session.TempForQuest < 0 || $session.TempForQuest == 0) {
                $reactions.answer("Вы хотели бы запланировать поездку в страну с холодным климатом?");
            }
            else if ($session.TempForQuest > 25 || $session.TempForQuest == 25) {
                $reactions.answer("Вы хотели бы запланировать поездку в страну с жарким климатом?");
            }

        state: YesSure
            q: * $comYes *
            q: верно
            q: * [это] (он/оно/то что нужно) *
            if: $session.StartPoint
                a: Вы хотели бы начать оформление нового тура в данную страну или хотели бы продолжить оформление старой заявки? 
            else: 
                a: Вы хотели бы начать оформление нового тура в данную страну? 

            state: New
                q: * $comYes *
                q: верно
                q: * [это] (он/оно/то что нужно) *
                q: * (новый/новую/нового/сначала) *
                q: * (заного/заново) *
                script:
                    #Начальная точка оформления заявки:
                    delete $session.StartPoint;
                    #Город отправления:
                    delete $session.departureCity;
                    #Дата отправления:
                    delete $session.departureDate;
                    #Дата возвращения:
                    delete $session.returnDate;
                    #Количество людей:
                    delete $session.people;
                    #Количество детей:
                    delete $session.children;
                    #Бюджет:
                    delete $session.bablo;
                    #Звезд у отеля:
                    delete $session.stars;
                    #Комментарий для менеджера:
                    delete $session.comments;
                go!: /Trip/TripStartPoint
        
            state: Old
                q: * (~продолжить) *
                q: * (~старая/старую/~прошлая/прошлую/прошлый/старой/предыдущей/предыдушую) *
                go!: /Trip/TripStartPoint
        
        state: NoSure
            q: * $comNo *
            q: * (не верно/неверно) *
            q: * [это] не то [что я хотел] *
            a: Хотите узнать о погоде в другом Городе/Стране?

            state: YesNoSure
                q: * $comYes *
                q: верно
                q: * [это] (он/оно/то что нужно) *
                script:
                    delete $session.arrivalPointForCity;
                    delete $session.arrivalPointForCountry;
                    delete $session.arrivalCoordinates; 
                go!: /Weather/WeatherQust  
        
            state: NoNoSure
                q: * $comNo *
                q: * (не верно/неверно) *
                q: * [это] не то [что я хотел] *
                a: К сожалению я больше ничем не могу Вам помочь.




#============================================= ОФОРМЛЕНИЕ ПУТЕВКИ =============================================#

theme:/Trip
    
    state: TripStartPoint
        q!: * (~Путевка/тур*/туристичес*/подбери (тур/путевку)) * 
        if: $session.StartPoint
            if: $session.departureCity
                go!: /Trip/TripStartPoint/DepartureCity 
            else:
                a: {{ $client.name }}, назовите, пожалуйста, город отправления. Если Вы ошибетесь в данных - не волнуйтесь, наш менеджер свяжется с Вами для уточнения данных.   
        else:
            a: {{ $client.name }}, назовите, пожалуйста, город отправления. Если Вы ошибетесь в данных - не волнуйтесь, наш менеджер свяжется с Вами для уточнения данных.
            script:
                $session.StartPoint = 1;
    
        state: DepartureCity
            q: * $City *
            if: $session.departureCity 
                if: $session.departureDate
                    go!: /Trip/TripStartPoint/DepartureCity/DepartureDate
                else:
                    a: Желаемая дата отправления?
            else:
                script:
                    $session.departureCity = $parseTree._City.name;
                if: $session.departureDate
                    go!: /Trip/TripStartPoint/DepartureCity/DepartureDate
                else:
                    a: Желаемая дата отправления?
            
            
            state: DepartureDate
                q: * (@duckling.date/@duckling.time) *
                if: $session.departureDate
                    if: $session.arrivalPointForCity  
                        go!: /Trip/TripStartPoint/DepartureCity/DepartureDate/ArrivalCity
                    elseif: $session.arrivalPointForCountry 
                        go!: /Trip/TripStartPoint/DepartureCity/DepartureDate/ArrivalCountry
                    else:
                        a: Куда бы вы хотели отправиться?
                else:
                    script:
                        $session.departureDate = $parseTree.value;
                    if: $session.arrivalPointForCity  
                        go!: /Trip/TripStartPoint/DepartureCity/DepartureDate/ArrivalCity
                    elseif: $session.arrivalPointForCountry 
                        go!: /Trip/TripStartPoint/DepartureCity/DepartureDate/ArrivalCountry
                    else:
                        a: Куда бы вы хотели отправиться?
                
                
                state: ArrivalCity
                    q: * $City *
                    if: $session.arrivalPointForCity  
                        if: $session.returnDate
                            go!: /Trip/ReturnDate
                        else:
                            a: Желаемая дата возвращения?
                            go: /Trip/ReturnDate
                    else:
                        script:
                            $session.arrivalPointForCity = $parseTree._City.name;
                        if: $session.returnDate
                            go!: /Trip/ReturnDate
                        else:
                            a: Желаемая дата возвращения?
                            go: /Trip/ReturnDate
                
                
                state: ArrivalCountry
                    q: * $Country *
                    if: $session.arrivalPointForCountry  
                        if: $session.returnDate
                            go!: /Trip/ReturnDate
                        else:
                            a: Желаемая дата возвращения?
                            go: /Trip/ReturnDate
                    else:
                        script:
                            $session.arrivalPointForCountry = $parseTree._Country.name;
                        if: $session.returnDate
                            go!: /Trip/ReturnDate
                        else:
                            a: Желаемая дата возвращения?
                            go: /Trip/ReturnDate
                
                
                state: CatchAll || noContext = true
                    event: noMatch
                    a: Простите, Но туда у нас нет туров. Выберете другую точку.
    
                    
    state: ReturnDate            
        q: * (@duckling.date/@duckling.time) *
        if: $session.returnDate
            if: $session.people
                go!: /Trip/ReturnDate/People
            else:
                a: Количество людей в поездке?
        else:
            script:
                $session.returnDate = $parseTree.value;
            if: $session.people
                go!: /Trip/ReturnDate/People
            else:
                a: Количество людей в поездке? 
        
        buttons:
            "уточню позже"    
            
        state: People
            q: *
            if: $session.people
                if: $session.children
                    go!: /Trip/ReturnDate/People/Children
                else:
                    a: Количество детей в поездке?
            else:
                script:
                    $session.people = $request.query;
                if: $session.children
                    go!: /Trip/ReturnDate/People/Children
                else:
                    a: Количество детей в поездке?
            
            buttons:
                "уточню позже"    
            
            state: Children
                q: * 
                if: $session.children
                    if: $session.bablo
                        go!: /Trip/ReturnDate/People/Children/Bablo
                    else:
                        a: Какой у Вас Бюджет?
                else:
                    script:
                        $session.children = $request.query;
                    if: $session.bablo
                        go!: /Trip/ReturnDate/People/Children/Bablo
                    else:
                        a: Какой у Вас Бюджет?
                 
                buttons:
                    "уточню позже"    
            
                state: Bablo
                    q: *
                    if: $session.bablo
                        if: $session.stars
                            go!: /Trip/ReturnDate/People/Children/Bablo/Stars
                        else:
                            a: Скольки звездочный отель ?
                    else:
                        script:
                            $session.bablo = $request.query;
                        if: $session.stars
                            go!: /Trip/ReturnDate/People/Children/Bablo/Stars
                        else:
                            a: Скольки звездочный отель ?
            
                    buttons:
                        "уточню позже"    
            
                    state: Stars
                        q: *
                        if: $session.stars
                            if: $session.comments
                                go!: /Trip/ReturnDate/People/Children/Bablo/Stars/Comments
                            else:
                                a: Комментарий для менеджера?
                        else:
                            script:
                                $session.stars = $request.query;
                            if: $session.comments
                                go!: /Trip/ReturnDate/People/Children/Bablo/Stars/Comments
                            else:
                                a: Комментарий для менеджера?
                 
                        buttons:
                            "нет комментариев"    
            
                        state: Comments
                            q: *
                            if: $session.comments
                                go!: /FullData/Screen
                            script:
                                $session.comments = $request.query;
                            go!: /FullData/Screen


#============================================= Вывод данных для пользователя и возможность изменить их =============================================#

            
theme: /FullData               
    
    state: Screen
        
        script:
            var answer = "";
            answer += $.client.name + ", подскажите, все ли верно:" + "\n";
            answer += "Пункт отправления: " + $.session.departureCity + "\n";
            answer += "Дата отправления: " + $.session.departureDate.year + "." + $.session.departureDate.month + "." + $.session.departureDate.day + "\n";
            if ($.session.arrivalPointForCity) {
                answer += "Пункт назначения: " + $.session.arrivalPointForCity + "\n";
            }
            if ($.session.arrivalPointForCountry) {
                answer += "Пункт назначения: " + $.session.arrivalPointForCountry + "\n";
            }
            answer += "Дата возвращения: " + $.session.returnDate.year + "." + $.session.returnDate.month + "." + $.session.returnDate.day + "\n";
            answer += "Количество людей: " + $.session.people + "\n";
            answer += "Количество детей: " + $.session.children + "\n";
            answer += "Бюджет: " + $.session.bablo + "\n";
            answer += "Звезд у отеля: " + $.session.stars + "\n";
            answer += "Комментарии для Менеджера: " + $.session.comments + "\n";
            $reactions.answer(answer);

        # a: {{ $client.name }}, подскажите, все ли верно: 
        # a: Город отправления: {{ $session.departureCity }}
        # a: Дата отправления: {{ $session.departureDate.year }} {{ $session.departureDate.month }} {{ $session.departureDate.day }}
        # a: Пункт назначения: {{ $session.arrivalPointForCity }} {{ $session.arrivalPointForCountry }}
        # a: Дата возвращения: {{ $session.returnDate.year }} {{ $session.returnDate.month }} {{ $session.returnDate.day }}
        # a: Количество людей: {{ $session.people }}
        # a: Количество детей: {{ $session.children }}
        # a: Бюджет: {{ $session.bablo }}
        # a: Звезд у отеля: {{ $session.stars }}
        # a: Комментарии для Менеджера: {{ $session.comments }}
        
        buttons:
            "верно"  
            "заполнить заново"
            "поменять данные"
        
        state: Yes
            q: * (да/верно) *
            a: Осталось самую малость.  
            if: $client.phone
               go!: /Phone/Confirm
            else:
                go!: /Phone/Ask
            
        state: No
            q: * заполнить заново *
            script:
                delete $session.StartPoint;
                delete $session.departureCity;
                delete $session.departureDate;
                delete $session.arrivalPointForCity;
                delete $session.arrivalPointForCountry;
                delete $session.returnDate;
                delete $session.people;
                delete $session.children;
                delete $session.bablo;
                delete $session.stars;
                delete $session.comments;
            go!: /Trip/TripStartPoint

        state: ChangeData
            q: * поменять данные *
            a: Чтобы вы хотели поменять?


            state: ChangeDepartureCity
                q: * (пункт/город/стран*) отправления *
                script:
                    delete $session.departureCity;
                go!: /Trip/TripStartPoint


            state: ChangeDepartureDate
                q: * (дата/дату) отправления *
                script:
                    delete $session.departureDate;
                go!: /Trip/TripStartPoint
            
            
            state: ChangeArrivalPoint
                q: * (пункт/город/стран*) назначения *
                script:
                    delete $session.arrivalPointForCity;
                    delete $session.arrivalPointForCountry;
                go!: /Trip/TripStartPoint
            
            
            state: ChangeReturnDate
                q: * (дата/дату) возвращения *
                script:
                    delete $session.returnDate;
                go!: /Trip/TripStartPoint
            
            
            state: ChangePeople
                q: * [количество/колво/кол-во] людей *
                script:
                    delete $session.people;
                go!: /Trip/TripStartPoint
            
            
            state: ChangeChildren
                q: * [количество/колво/кол-во] детей *
                script:
                    delete $session.children;
                go!: /Trip/TripStartPoint


            state: ChangeBablo
                q: * (бюджет/буджет/*юджет) *
                script:
                    delete $session.bablo;
                go!: /Trip/TripStartPoint
        
        
            state: ChangeStars
                q: * (звезд/звезды/звездность) [у] [отеля] *
                script:
                    delete $session.stars;
                go!: /Trip/TripStartPoint
        
            state: ChangeComments
                q: * (Комментарии/ком/комент*/коммент*/доп информацию) [для] [менеджера] *
                script:
                    delete $session.comments;
                go!: /Trip/TripStartPoint
        
        



#============================================= Запрос телефона и окончание формирования путевки =============================================#


theme: /Phone
    state: Ask 
        a: Для передачи заявки менеджеру, мне нужен Ваш номер телефона в формате 79000000000.

        state: Get
            q: $phone
            go!: /Phone/Confirm

        state: Wrong
            q: *
            a: О-оу. ошибка в формате набора номера. Проверьте.
            go!: /Phone/Ask


    state: Confirm
        script:
            $temp.phone = $parseTree._phone || $client.phone;

        a: Ваш номер {{ $temp.phone }}, верно?

        script:
            $session.probablyPhone = $temp.phone;

        buttons:
            "Да"
            "Нет"

        state: Yes
            q: (да/верно)
            script:
                $client.phone = $session.probablyPhone;
                delete $session.probablyPhone;
            go!: /SendMail/Mail
            

        state: No
            q: (нет/не [верно])
            go!: /Phone/Ask
            
            
#============================================= Отправка формы на почту менеджеру =============================================#            
        
theme: /SendMail
    state: Mail
        script:
            var ClientData = "";
            ClientData += " Имя: " + $.client.name + "\n";
            ClientData += " Пункт отправления: " + $.session.departureCity + "\n";
            ClientData += " Дата отправления: " + $.session.departureDate.year + "." + $.session.departureDate.month + "." + $.session.departureDate.day + "\n";
            if ($.session.arrivalPointForCity) {
                ClientData += " Пункт назначения: " + $.session.arrivalPointForCity + "\n";
            }
            if ($.session.arrivalPointForCountry) {
                ClientData += " Пункт назначения: " + $.session.arrivalPointForCountry + "\n";
            }
            ClientData += " Дата возвращения: " + $.session.returnDate.year + "." + $.session.returnDate.month + "." + $.session.returnDate.day + "\n";
            ClientData += " Количество людей: " + $.session.people + "\n";
            ClientData += " Количество детей: " + $.session.children + "\n";
            ClientData += " Бюджет: " + $.session.bablo + "\n";
            ClientData += " Звезд у отеля: " + $.session.stars + "\n";
            ClientData += " Комментарии для Менеджера: " + $.session.comments + "\n";
            ClientData += " Телефон: " + $client.phone + "\n";
            $mail.sendMessage("Адрес почты","Заявка от клиента1",ClientData);
            $reactions.answer("В ближайшее время с Вами свяжется менеджер компании. До свидания.");
