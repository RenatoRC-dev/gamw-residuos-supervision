import 'package:flutter/material.dart';
import 'package:flutter/foundation.dart';
import '../modules/ejecucion/jornadas/presentacion/panel_ejecucion_tecnica.dart';
import '../modules/ejecucion/jornadas/presentacion/pagina_operacion.dart';

class GamwApp extends StatelessWidget {
  const GamwApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'GAMW',
      theme: ThemeData(colorSchemeSeed: const Color(0xFF075F4B)),
      home: Scaffold(
        appBar: AppBar(title: const Text('GAMW · Ejecución operativa')),
        body: SafeArea(
          child: Padding(
            padding: EdgeInsets.all(24),
            child: SingleChildScrollView(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  if (kDebugMode &&
                      const bool.fromEnvironment('GAMW_EJECUCION_DESARROLLO'))
                    const PanelEjecucionTecnica(
                      jornadaId: String.fromEnvironment('GAMW_JORNADA_ID'),
                      asignacionId: String.fromEnvironment(
                        'GAMW_ASIGNACION_JORNADA_ID',
                      ),
                      apiUrl: String.fromEnvironment(
                        'GAMW_API_URL',
                        defaultValue: 'http://127.0.0.1:8080/api',
                      ),
                    )
                  else
                    const PaginaOperacion(),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
