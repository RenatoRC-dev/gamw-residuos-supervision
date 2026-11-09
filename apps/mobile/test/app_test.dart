import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gamw_mobile/app/gamw_app.dart';

void main() {
  testWidgets('arranca la superficie operativa sin permisos GPS', (
    tester,
  ) async {
    await tester.pumpWidget(const GamwApp());
    await tester.pump();
    expect(find.text('GAMW · Ejecución operativa'), findsOneWidget);
    expect(find.text('Acceso GAMW'), findsOneWidget);
    expect(find.byType(Scaffold), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
