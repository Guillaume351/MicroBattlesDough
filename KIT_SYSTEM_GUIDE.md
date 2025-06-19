# Guide du Système de Kits MicroBattles

## Vue d'ensemble

Le système de kits de MicroBattles a été complètement remanié pour inclure :

- **Système de niveaux et de pièces** pour débloquer les kits
- **Interface utilisateur cross-platform** (Java et Bedrock via GeyserMC)
- **Équilibrage des kits** avec prix et niveaux requis
- **Descriptions détaillées** pour chaque kit

## Fonctionnalités

### Système de Progression

- **Pièces (Coins)** : Monnaie du jeu pour acheter des kits
- **Niveaux** : Certains kits nécessitent un niveau minimum
- **Kits débloqués** : Sauvegardés dans la base de données du joueur

### Interface Utilisateur

- **Joueurs Java** : Interface d'inventaire avec items cliquables
- **Joueurs Bedrock** : Formulaires GeyserMC avec boutons

## Kits Disponibles

### Kits Gratuits (Niveau 0)

1. **Default** - Kit de base équilibré (0 pièces)
2. **Knockback Warrior** - "The Big Stick" avec Knockback V (50 pièces)
3. **Archer** - Arc puissant avec Power II (0 pièces)

### Kits Intermédiaires

4. **Miner** - Pioche efficace et blocs de construction (80 pièces, Niveau 3)
5. **Trapper** - Pièges et fils de fer (100 pièces, Niveau 5)
6. **Explosive Archer** - Flèches explosives (120 pièces, Niveau 7)
7. **Berserker** - Hache puissante, bonus de force à faible vie (150 pièces, Niveau 8)

### Kits Avancés

8. **Alchemist** - Station de brassage et ingrédients (200 pièces, Niveau 12)
9. **Vampire** - Lame vampirique avec vol de vie (220 pièces, Niveau 14)
10. **Tank** - Armure diamant, lenteur et résistance permanentes (230 pièces, Niveau 15)
11. **Enderman** - Perles d'ender et téléportation (250 pièces, Niveau 16)

### Kits Experts

12. **Chemist** - Potions de dégâts et d'effets négatifs (280 pièces, Niveau 20)
13. **Ninja** - Vitesse et invisibilité (300 pièces, Niveau 22)
14. **Frost Mage** - Baguette de glace et boules de neige ralentissantes (320 pièces, Niveau 25)
15. **Assassin** - Dague critique et invisibilité (350 pièces, Niveau 28)

### Kit Légendaire

16. **Juggernaut** - Équipement Netherite, très tanky mais lent (400 pièces, Niveau 30)

## Commandes

### `/kit`

Ouvre l'interface de sélection des kits appropriée selon le type de client (Java/Bedrock).

## Intégration Technique

### Base de Données

Les nouvelles colonnes ajoutées à `PlayerData` :

- `coins` : Nombre de pièces du joueur
- `level` : Niveau actuel du joueur
- `unlocked_kits` : Liste des kits débloqués (JSON)

### Migration de Base de Données

Exécutez le script `database_migration.sql` pour mettre à jour la base de données existante :

```sql
-- Ajouter les nouvelles colonnes avec des valeurs par défaut
ALTER TABLE player_data
ADD COLUMN IF NOT EXISTS coins INTEGER DEFAULT 100,
ADD COLUMN IF NOT EXISTS level INTEGER DEFAULT 1,
ADD COLUMN IF NOT EXISTS unlocked_kits TEXT DEFAULT '[]';

-- Mettre à jour les joueurs existants avec des valeurs de départ
UPDATE player_data
SET coins = 100, level = 1, unlocked_kits = '[]'
WHERE coins IS NULL OR level IS NULL OR unlocked_kits IS NULL;
```

### Dépendances

Le module MicroBattles nécessite :

- **GeyserMC API** : Pour la détection des joueurs Bedrock
- **Cumulus** : Pour les formulaires Bedrock
- **CookieDough** : Pour l'accès aux données des joueurs

## Fonctionnalités Spéciales à Implémenter

Certains kits ont des capacités spéciales qui nécessitent des listeners personnalisés :

### Explosive Archer

- Les flèches créent de petites explosions à l'impact

### Berserker

- Gagne Strength I quand la vie est en dessous de 5 cœurs

### Assassin

- Premier coup après invisibilité inflige 2x les dégâts

### Vampire

- 25% des dégâts infligés sont convertis en vie

### Frost Mage

- Clic droit avec la baguette tire des boules de neige ralentissantes

## Système d'Expérience et de Récompenses

### Gain d'Expérience

- **Victoire** : +50 XP + 25 pièces
- **Défaite** : +10 XP + 5 pièces (participation)
- **Kill** : +5 XP + 2 pièces par kill
- **Mort** : Aucun gain (mais comptabilisée dans les stats)

### Calcul des Niveaux

- Formule : `niveau = sqrt(experience / 100) + 1`
- Niveau 1 : 0-99 XP
- Niveau 2 : 100-399 XP
- Niveau 3 : 400-899 XP
- etc.

## Utilisation

1. **Démarrage du serveur** : Le système se charge automatiquement
2. **Joueurs** : Utilisent `/kit` pour accéder à l'interface
3. **Progression** : Les joueurs gagnent XP et pièces en jouant
4. **Achat** : Les joueurs peuvent acheter des kits s'ils ont assez de pièces et le niveau requis
5. **Sélection** : Les kits débloqués peuvent être sélectionnés pour les parties

## Points d'Intégration Futurs

- **Effets spéciaux** : Implémenter les capacités uniques des kits
- **Personnalisation** : Permettre la configuration des prix et niveaux requis
- **Statistiques avancées** : Leaderboards par mini-jeu
- **Récompenses saisonnières** : Événements spéciaux avec bonus XP/pièces
