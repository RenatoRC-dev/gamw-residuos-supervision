import 'package:flutter/material.dart';

class GamwApp extends StatelessWidget {
  const GamwApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'GAMW',
      theme: ThemeData(colorSchemeSeed: const Color(0xFF075F4B)),
      home: Scaffold(
        appBar: AppBar(title: const Text('GAMW · Base técnica')),
        body: const SafeArea(
          child: Padding(
            padding: EdgeInsets.all(24),
            child: Text('PHASE 0 — ENGINEERING FOUNDATION'),
          ),
        ),
      ),
    );
  }
}
