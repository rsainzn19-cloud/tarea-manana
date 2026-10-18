# One source of truth for the Shorts in both languages.
# text: what the captions show (*highlighted*); say: what the voice reads when the spelling needs help.
# card/wall: on-screen graphics; card 'at' is a fraction of the spoken line.
SHORTS = {
 'piloto': {
  'title': {'es': 'Piloto contra piloto', 'en': 'Pilot vs. pilot'},
  'hook': {'es': 'Un piloto trató de estrellar el avión en pleno vuelo', 'en': 'A pilot tried to crash the plane mid-flight'},
  'beats': [
   {'es': {'text': 'Un piloto *apuñaló* a su compañero en pleno vuelo… y trató de *estrellar* el avión.'},
    'en': {'text': 'A pilot *stabbed* the other pilot mid-flight… and tried to *crash* the plane.'}},
   {'es': {'text': 'Era un vuelo de FlyDubai de *Dubái a Tel Aviv*, con unos *170 pasajeros*.', 'say': 'Era un vuelo de Flai Dubái, de Dubái a Tel Aviv, con unos ciento setenta pasajeros.', 'card': ['170', 'PASAJEROS A BORDO', 0.55]},
    'en': {'text': 'It was a FlyDubai flight from *Dubai to Tel Aviv*, with about *170 passengers* on board.', 'say': 'It was a Fly Dubai flight from Dubai to Tel Aviv, with about a hundred and seventy passengers on board.', 'card': ['170', 'PASSENGERS ON BOARD', 0.6]}},
   {'es': {'text': 'Según la AP, el avión bajó de *33,000* a *17,000 pies*… en cerca de *un minuto*.', 'say': 'Según la A.P., el avión bajó de treinta y tres mil a diecisiete mil pies... en cerca de un minuto.', 'card': ['−16,000 PIES', 'EN CERCA DE 1 MINUTO', 0.4]},
    'en': {'text': 'According to the AP, the plane dropped from *33,000* to *17,000 feet*… in about *one minute*.', 'say': 'According to the A.P., the plane dropped from thirty-three thousand to seventeen thousand feet... in about one minute.', 'card': ['−16,000 FT', 'IN ABOUT 1 MINUTE', 0.4]}},
   {'es': {'text': 'Son casi *5 kilómetros* de caída.', 'say': 'Son casi cinco kilómetros de caída.', 'card': ['≈ 5 KM', 'DE CAÍDA', 0.05]},
    'en': {'text': 'That’s almost *3 miles* of altitude, gone.', 'say': 'That’s almost three miles of altitude, gone.', 'card': ['≈ 3 MILES', 'OF ALTITUDE LOST', 0.05]}},
   {'es': {'text': 'Pero el piloto herido logró *abrir la puerta* de la cabina…', 'wall': ['EL GIRO', 'LA PUERTA', 'DE LA CABINA']},
    'en': {'text': 'But the injured pilot managed to *open the cockpit door*…', 'wall': ['THE TWIST', 'THE DOOR', 'TO THE COCKPIT']}},
   {'es': {'text': '…y un pasajero y la tripulación *sometieron* al atacante.'},
    'en': {'text': '…and a passenger and the crew *overpowered* the attacker.'}},
   {'es': {'text': 'El avión aterrizó a salvo en *Tabuk*, Arabia Saudita.', 'card': ['TABUK', 'ARABIA SAUDITA', 0.35]},
    'en': {'text': 'The plane landed safely in *Tabuk*, Saudi Arabia.', 'say': 'The plane landed safely in Tabook, Saudi Arabia.', 'card': ['TABUK', 'SAUDI ARABIA', 0.4]}},
   {'es': {'text': 'Los dos pilotos acabaron en el hospital, Arabia Saudita *arrestó* a uno… y el *motivo* sigue sin aclararse.', 'card': ['¿POR QUÉ?', 'MOTIVO SIN ACLARAR', 0.72]},
    'en': {'text': 'Both pilots ended up in the hospital, Saudi Arabia *arrested* one of them… and the *motive* is still unclear.', 'card': ['WHY?', 'MOTIVE STILL UNCLEAR', 0.72]}},
   {'es': {'text': '¿Tú te volverías a subir a un avión después de esto? *¡Quédate cuadrado!*', 'wall': ['TU TURNO', '¿Y TÚ?', '¿TE SUBIRÍAS OTRA VEZ?']},
    'en': {'text': 'Would you ever get on a plane again after this? *Stay square!*', 'wall': ['YOUR TURN', 'WOULD YOU?', 'FLY AGAIN AFTER THIS?']}},
  ]},
 'dolar': {
  'title': {'es': 'Un dólar, 2.5 millones', 'en': 'One dollar, 2.5 million'},
  'hook': {'es': 'En Irán un dólar ya cuesta 2.5 millones', 'en': 'In Iran one dollar now costs 2.5 million'},
  'beats': [
   {'es': {'text': '¡Oigan! En Irán, *un solo dólar* ya cuesta más de *2.5 millones* de riales.', 'say': '¡Oigan! En Irán, un solo dólar ya cuesta más de dos millones y medio de riales.', 'card': ['1 USD = 2.5 M', 'RIALES · NUEVO RÉCORD', 0.55]},
    'en': {'text': 'In Iran, *a single dollar* now costs more than *2.5 million* rials.', 'say': 'In Iran, a single dollar now costs more than two and a half million ree-yahls.', 'card': ['1 USD = 2.5 M', 'RIALS · NEW RECORD LOW', 0.55]}},
   {'es': {'text': 'Hace apenas *27 días*, el récord era de *2.2 millones*.', 'say': 'Hace apenas veintisiete días, el récord era de dos punto dos millones.', 'card': ['2.2 M → 2.5 M', 'EN 27 DÍAS', 0.25]},
    'en': {'text': 'Just *27 days* earlier, the record was *2.2 million*.', 'say': 'Just twenty-seven days earlier, the record was two point two million.', 'card': ['2.2 M → 2.5 M', 'IN 27 DAYS', 0.25]}},
   {'es': {'text': '¿Por qué? Irán está en *guerra* desde febrero, con un *bloqueo naval* de Estados Unidos y nuevas *sanciones*.'},
    'en': {'text': 'Why? Iran has been at *war* since February, under a US *naval blockade* and new *sanctions*.', 'say': 'Why? Iran has been at war since February, under a U.S. naval blockade and new sanctions.'}},
   {'es': {'text': 'Luego, Irán propuso reabrir el estrecho de *Ormuz* en siete días… y Trump lo *rechazó*.', 'card': ['RECHAZADO', 'PLAN DE 7 DÍAS PARA ORMUZ', 0.75]},
    'en': {'text': 'Iran offered to reopen the Strait of *Hormuz* within seven days… and Trump *rejected* it.', 'say': 'Iran offered to reopen the Strait of Hor-moose within seven days... and Trump rejected it.', 'card': ['REJECTED', '7-DAY HORMUZ PLAN', 0.75]}},
   {'es': {'text': 'Ahora *Catar* intenta mediar entre los dos.', 'wall': ['MEDIADOR', 'CATAR', 'ENTRE IRÁN Y EE. UU.']},
    'en': {'text': 'Now *Qatar* is trying to mediate between the two.', 'wall': ['MEDIATOR', 'QATAR', 'BETWEEN IRAN AND THE US']}},
   {'es': {'text': 'Y esto te toca: por Ormuz pasa cerca del *20%* del petróleo que consume el mundo.', 'say': 'Y esto te toca: por Ormuz pasa cerca del veinte por ciento del petróleo que consume el mundo.', 'card': ['20%', 'DEL PETRÓLEO DEL MUNDO', 0.5]},
    'en': {'text': 'And this hits you too: about *20%* of the oil the world uses goes through the Strait of Hormuz.', 'say': 'And this hits you too: about twenty percent of the oil the world uses goes through the Strait of Hor-moose.', 'card': ['20%', 'OF THE WORLD’S OIL', 0.4]}},
   {'es': {'text': 'En Estados Unidos, la gasolina ya promedia *4.48 dólares* por galón: récord para esta época del año.', 'say': 'En Estados Unidos, la gasolina ya promedia cuatro dólares con cuarenta y ocho centavos por galón: récord para esta época del año.', 'card': ['$4.48', 'POR GALÓN · AAA', 0.4]},
    'en': {'text': 'In the US, gas now averages *$4.48* a gallon, a record for this time of year.', 'say': 'In the U.S., gas now averages four dollars and forty-eight cents a gallon, a record for this time of year.', 'card': ['$4.48', 'PER GALLON · AAA', 0.35]}},
   {'es': {'text': '¿Tú qué harías si tu dinero perdiera valor así de rápido? *¡Quédate cuadrado!*', 'wall': ['TU TURNO', '¿Y TÚ?', '¿QUÉ HARÍAS CON TU DINERO?']},
    'en': {'text': 'What would you do if your money lost value this fast? *Stay square!*', 'wall': ['YOUR TURN', 'AND YOU?', 'WHAT WOULD YOU DO?']}},
  ]},
 'papa': {
  'title': {'es': 'Papá… en el espacio', 'en': 'A dad… in space'},
  'hook': {'es': 'Su bebé va a nacer mientras él está en órbita', 'en': 'His baby will be born while he is in orbit'},
  'beats': [
   {'es': {'text': 'Este astronauta se va *seis meses* al espacio… y su bebé va a nacer mientras él está *allá arriba*.'},
    'en': {'text': 'This astronaut is spending *six months* in space… and his baby will be born while he’s *up there*.'}},
   {'es': {'text': 'Es el canadiense *Joshua Kutryk*, y despegó el 1 de octubre con la misión *Crew-13*.', 'say': 'Es el canadiense Yóshua Kútrik, y despegó el primero de octubre con la misión Cru Trece.', 'card': ['CREW-13', 'EE. UU. · CANADÁ · RUSIA', 0.75]},
    'en': {'text': 'He’s Canadian astronaut *Joshua Kutryk*, and he launched on October 1st with *Crew-13*.', 'say': 'He’s Canadian astronaut Joshua Kootrick, and he launched on October first with Crew Thirteen.', 'card': ['CREW-13', 'US · CANADA · RUSSIA', 0.75]}},
   {'es': {'text': 'Y su esposa espera a su *tercer hijo* para *finales de noviembre*.', 'card': ['NOVIEMBRE', 'LLEGA SU TERCER HIJO', 0.45]},
    'en': {'text': 'His wife is expecting their *third child* in *late November*.', 'card': ['NOVEMBER', 'BABY NUMBER THREE', 0.6]}},
   {'es': {'text': 'Así que el bebé va a nacer… mientras él flota a unos *400 kilómetros* de altura.', 'say': 'Así que el bebé va a nacer... mientras él flota a unos cuatrocientos kilómetros de altura.', 'card': ['≈ 400 KM', 'DE ALTURA', 0.6]},
    'en': {'text': 'So the baby will arrive… while he floats about *250 miles* above Earth.', 'say': 'So the baby will arrive... while he floats about two hundred and fifty miles above Earth.', 'card': ['≈ 250 MILES', 'ABOVE EARTH', 0.6]}},
   {'es': {'text': 'Él sería apenas la *cuarta persona* en ser papá mientras está en el espacio.', 'card': ['#4', 'PAPÁS EN ÓRBITA', 0.3]},
    'en': {'text': 'He’d be only the *fourth person* ever to become a dad while in space.', 'card': ['#4', 'DADS IN ORBIT', 0.35]}},
   {'es': {'text': 'Y hasta el viaje fue de récord: llegaron a la estación en solo *8 horas*.', 'say': 'Y hasta el viaje fue de récord: llegaron a la estación en solo ocho horas.', 'card': ['8 HORAS', 'EL VIAJE MÁS RÁPIDO DE EE. UU.', 0.7]},
    'en': {'text': 'Even the trip broke a record: they reached the station in just *8 hours*.', 'say': 'Even the trip broke a record: they reached the station in just eight hours.', 'card': ['8 HOURS', 'FASTEST US RIDE TO THE ISS', 0.7]}},
   {'es': {'text': '¿Tú aceptarías perderte el nacimiento de tu hijo por ir al espacio? *¡Quédate cuadrado!*', 'wall': ['TU TURNO', '¿Y TÚ?', '¿TE IRÍAS AL ESPACIO?']},
    'en': {'text': 'Would you miss your child’s birth to go to space? *Stay square!*', 'wall': ['YOUR TURN', 'WOULD YOU?', 'MISS IT FOR SPACE?']}},
  ]},
}

# Mr. News: his catchphrases, for the character sheet and the show
PHRASES = [
 {'id': 'hola', 'es': {'text': '¡Qué onda, cubos! Aquí Mr. News.', 'say': '¡Qué onda, cubos! Aquí Míster Nius.'}, 'en': {'text': 'What’s up, cubes! Mr. News here.', 'say': "What's up, cubes! Mister News here."}},
 {'id': 'bloque', 'es': {'text': '¡Y ahora… el siguiente bloque!', 'say': 'Y ahora, el siguiente bloque.'}, 'en': {'text': 'And now… the next block!', 'say': 'And now, the next block!'}},
 {'id': 'adios', 'es': {'text': '¡Quédate cuadrado y bien informado!'}, 'en': {'text': 'Stay square, stay informed!'}},
]
