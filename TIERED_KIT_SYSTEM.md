# Système de Kits à Niveaux - MicroBattles

## Vue d'ensemble

Le nouveau système de kits à niveaux remplace l'ancien système de kits fixes par un système de progression où chaque kit peut être amélioré à travers 3 niveaux (I, II, III). Les joueurs peuvent débloquer et améliorer leurs kits en utilisant des pièces et en atteignant certains niveaux.

## Kits Disponibles

### 1. Archer (Arrow)

- **Niveau I** : Arc Power I, 32 flèches
- **Niveau II** : Arc Power II, 48 flèches
- **Niveau III** : Arc Power III, 64 flèches
- **Spécialité** : Combat à distance

### 2. Mobilité (Mobility)

- **Niveau I** : Speed I, Jump Boost I
- **Niveau II** : Speed II, Jump Boost I
- **Niveau III** : Speed II, Jump Boost II
- **Spécialité** : Vitesse et agilité

### 3. Grimpeur (Climber)

- **Niveau I** : Jump Boost II, 16 blocs de cobweb
- **Niveau II** : Jump Boost III, 24 blocs de cobweb
- **Niveau III** : Jump Boost III, 32 blocs de cobweb + Speed I
- **Spécialité** : Escalade et mobilité verticale

### 4. Assommoir (Knockback)

- **Niveau I** : Épée Knockback I
- **Niveau II** : Épée Knockback II
- **Niveau III** : Épée Knockback II + bâton Knockback II
- **Spécialité** : Repousser les ennemis

### 5. Démolisseur (TNT)

- **Niveau I** : 4 TNT, briquet
- **Niveau II** : 6 TNT, briquet
- **Niveau III** : 8 TNT, briquet + 16 redstone
- **Spécialité** : Explosifs et destruction

### 6. Alchimiste (Alchemist)

- **Niveau I** : 2 potions de soin, 2 potions de vitesse
- **Niveau II** : 3 potions de soin, 3 potions de vitesse, 2 potions de force
- **Niveau III** : 4 potions de soin, 4 potions de vitesse, 3 potions de force, 2 potions d'invisibilité
- **Spécialité** : Potions et effets magiques

### 7. Enderman (Enderman)

- **Niveau I** : 4 perles d'ender
- **Niveau II** : 6 perles d'ender
- **Niveau III** : 8 perles d'ender + Speed I
- **Spécialité** : Téléportation et mobilité

## Système de Prix et Niveaux

### Structure des Prix

- **Niveau I** : Prix de base abordable (50-200 pièces)
- **Niveau II** : Prix modéré (100-400 pièces)
- **Niveau III** : Prix élevé (200-800 pièces)

### Niveaux Requis

- **Niveau I** : Niveau 1 (accessible immédiatement)
- **Niveau II** : Niveau 5-10
- **Niveau III** : Niveau 15-25

## Interface Utilisateur

### Menu Principal

- Affichage en grille des 7 kits disponibles
- Indicateurs visuels : VERROUILLÉ, POSSÉDÉ, SÉLECTIONNÉ
- Clic gauche : Sélectionner/Acheter
- Clic droit : Voir les niveaux disponibles

### Menu des Niveaux

- Affichage des 3 niveaux pour un kit spécifique
- Informations détaillées : prix, niveau requis, équipement
- Progression visuelle des améliorations

## Intégration Technique

### Classes Principales

- `TieredKit` : Définition des kits et de leurs niveaux
- `TieredKitManager` : Gestion des kits, achats, sélection
- `TieredKitSelectionUI` : Interface utilisateur
- `TieredKitSelectorListener` : Gestion des événements

### Base de Données

- Utilise `MinigameProgressionService` pour stocker :
  - Kits débloqués par joueur
  - Kit actuellement sélectionné
  - Statistiques de progression

### Traductions

- Support complet français/anglais
- Clés de traduction dans `messages_fr.properties` et `messages_en.properties`
- Préfixe `kit.tiered.*` pour les nouveaux messages

## Migration depuis l'Ancien Système

Le nouveau système coexiste avec l'ancien système de kits :

1. Les joueurs peuvent toujours utiliser les anciens kits
2. Le nouveau système est prioritaire si un kit à niveaux est sélectionné
3. Fallback automatique vers l'ancien système si aucun kit à niveaux n'est sélectionné

## Utilisation

### Pour les Joueurs

1. Utiliser le cookie "Sélection de Kit" dans le lobby
2. Naviguer dans l'interface intuitive
3. Acheter et améliorer les kits avec les pièces gagnées
4. Sélectionner le kit désiré avant de rejoindre une partie

### Pour les Développeurs

```java
// Obtenir le gestionnaire de kits
TieredKitManager manager = TieredKitManager.getInstance();

// Vérifier si un joueur possède un kit
boolean hasKit = manager.hasKit(statsService, playerId, kitType, tier);

// Équiper un kit sélectionné
boolean equipped = manager.equipSelectedKit(player, statsService);

// Ouvrir l'interface de sélection
TieredKitSelectionUI.openKitSelection(player, statsService);
```

## Avantages du Nouveau Système

1. **Progression** : Les joueurs ont des objectifs à long terme
2. **Équilibrage** : Les kits de niveau I sont moins puissants mais accessibles
3. **Économie** : Utilisation des pièces du jeu pour les achats
4. **Flexibilité** : Système extensible pour ajouter de nouveaux kits
5. **Intuitivité** : Interface claire et facile à utiliser

## Configuration

Le système est automatiquement initialisé au démarrage du plugin MicroBattles. Aucune configuration supplémentaire n'est requise.
