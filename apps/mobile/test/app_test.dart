import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:gamw_mobile/app/gamw_app.dart';

void main() {
  testWidgets('arranca la base técnica sin permisos ni servicios externos', (
    tester,
  ) async {
    await tester.pumpWidget(const GamwApp());
    expect(find.text('GAMW · Base técnica'), findsOneWidget);
    expect(find.text('PHASE 0 — ENGINEERING FOUNDATION'), findsOneWidget);
    expect(find.byType(Scaffold), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
