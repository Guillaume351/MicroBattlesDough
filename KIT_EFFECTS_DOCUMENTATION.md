# Documentation des Effets de Kits - MicroBattles

## Vue d'ensemble des améliorations

Les effets des kits ont été complètement refactorisés pour être plus équilibrés, utiles et amusants. Chaque kit avec des capacités spéciales a maintenant des effets bien implémentés qui justifient leur prix et leur niveau requis.

## Kits avec Effets Spéciaux

### 🧊 Frost Mage (320 coins, niveau 25)

**Capacités passives :**

- **Maîtrise du Froid** : Sur glace/neige, gagne Speed II + Resistance I
- **Adaptation Arctique** : Bonus dans les biomes froids (température < 0.15)

**Capacité active - Frost Wand :**

- **Cooldown** : 8 secondes
- **Pont de Glace** : Crée un pont de 20 blocs de glace compacte
- **Zone de Gel** : Ralentit les ennemis dans un rayon de 8 blocs (Slowness III + Mining Fatigue I)
- **Durée** : Le pont disparaît après 8 secondes
- **Boules de Neige Magiques** : Les snowballs lancées avec le wand sont améliorées (vitesse +50%, zone de gel à l'impact)

### 🧛 Vampire (220 coins, niveau 14)

**Capacités passives :**

- **Lifesteal Amélioré** : Récupère 50% des dégâts infligés en vie (max 2 cœurs par attaque)
- **Morsure Vampirique** : 25% de chance d'infliger Weakness I à la victime
- **Effets Visuels** : Particules de cœur lors de la guérison

### ⚔️ Berserker (150 coins, niveau 8)

**Capacités passives :**

- **Rage Conditionnelle** : Plus la vie est basse, plus les bonus sont importants
  - À 50% de vie ou moins : Strength I + Speed I
  - À 25% de vie ou moins : Strength II + Speed I + message "RAGE BERSERKER ACTIVÉE !"
- **Effet Sonore** : Hurlement de loup lors de l'activation de la rage maximale

### 🗡️ Assassin (350 coins, niveau 28)

**Capacités passives :**

- **Attaque Sournoise** : Premier coup après invisibilité inflige 200% de dégâts
- **Furtivité Avancée** : Double-sneak pour activer l'invisibilité (cooldown 20s)

**Capacité active - Invisibilité :**

- **Durée** : 6 secondes d'invisibilité + Speed II + Night Vision
- **Bonus d'Attaque** : Marque le joueur pour l'attaque sournoise
- **Effets Visuels** : Particules de fumée et son d'illusionniste

### 🥷 Ninja (300 coins, niveau 22)

**Capacités passives :**

- **Furtivité** : Double-sneak pour activer l'invisibilité (cooldown 25s)

**Capacité active - Mode Furtif :**

- **Durée** : 6 secondes d'invisibilité + Speed II + Night Vision
- **Activation** : Double-sneak rapide (moins de 0.5s entre les deux)
- **Effets Visuels** : Particules de fumée

### 🧪 Alchemist (200 coins, niveau 12)

**Capacité active - Brassage Aléatoire :**

- **Cooldown** : 12 secondes
- **4 Types de Potions Possibles** :
  1. **Combat** : Strength II + Resistance I (15s)
  2. **Mobilité** : Speed III + Jump Boost II (20s)
  3. **Guérison** : Regeneration III + Absorption II (10s/30s)
  4. **Tactique** : Invisibility + Night Vision (5s/30s)
- **Effets Visuels** : Particules de sorcière + sons de brassage

### 💥 Explosive Archer (120 coins, niveau 7)

**Capacités passives :**

- **Flèches Explosives** : Toutes les flèches créent une explosion à l'impact
- **Explosion Améliorée** : Rayon de 2.5 blocs, 6 cœurs de dégâts directs
- **Propulsion** : Les ennemis touchés sont projetés en arrière
- **Sécurité** : N'endommage pas les blocs du terrain

## Équilibrage et Justification des Prix

### Kits de Niveau Débutant (0-10)

- **Default** (gratuit) : Kit équilibré de base
- **Archer** (gratuit) : Spécialisé à distance
- **Knockback Warrior** (50 coins) : Contrôle de zone simple
- **Explosive Archer** (120 coins, niveau 7) : Premier kit avec effets spéciaux

### Kits de Niveau Intermédiaire (11-20)

- **Berserker** (150 coins, niveau 8) : Risque/récompense avec la vie basse
- **Alchemist** (200 coins, niveau 12) : Polyvalence avec les potions
- **Vampire** (220 coins, niveau 14) : Sustain en combat

### Kits de Niveau Avancé (21-30)

- **Frost Mage** (320 coins, niveau 25) : Contrôle de zone puissant
- **Ninja** (300 coins, niveau 22) : Mobilité et furtivité
- **Assassin** (350 coins, niveau 28) : Burst damage élevé
- **Juggernaut** (400 coins, niveau 30) : Tank ultime

## Mécaniques de Cooldown

Tous les cooldowns sont équilibrés pour éviter le spam tout en gardant les capacités utilisables :

- **Capacités Offensives** : 8-12 secondes
- **Capacités Défensives/Utilitaires** : 12-15 secondes
- **Capacités Puissantes** : 20-25 secondes

## Effets Visuels et Audio

Chaque capacité a des effets visuels et sonores distinctifs :

- **Particules** : Indiquent le type d'effet (cœurs pour vampire, flocons pour frost mage, etc.)
- **Sons** : Feedback audio pour l'activation et l'impact
- **Messages** : Notifications claires pour le joueur et les victimes

## Notes Techniques

### Optimisations

- Utilisation de Maps pour gérer les cooldowns par joueur
- Vérification de l'état du jeu avant d'appliquer les effets
- Nettoyage automatique des effets temporaires

### Compatibilité

- Compatible avec tous les types de projectiles
- Gestion des collisions et des zones d'effet
- Respect des mécaniques de base de Minecraft

### Sécurité

- Vérifications de nullité pour éviter les erreurs
- Limitation des effets aux joueurs en jeu
- Protection contre l'exploitation des cooldowns

## Recommandations d'Utilisation

### Pour les Nouveaux Joueurs

1. Commencer avec **Default** ou **Archer**
2. Économiser pour **Knockback Warrior** (contrôle simple)
3. Progresser vers **Explosive Archer** (premiers effets spéciaux)

### Pour les Joueurs Intermédiaires

1. **Berserker** pour un gameplay agressif
2. **Alchemist** pour la polyvalence
3. **Vampire** pour la survie en combat

### Pour les Joueurs Avancés

1. **Frost Mage** pour le contrôle de zone
2. **Assassin** pour les éliminations rapides
3. **Juggernaut** pour tanker l'équipe

## Équilibrage Futur

Les effets peuvent être ajustés selon les retours des joueurs :

- **Cooldowns** : Peuvent être modifiés selon l'usage
- **Dégâts** : Équilibrage basé sur les statistiques de jeu
- **Durées** : Ajustement selon l'impact sur le gameplay
- **Nouveaux Effets** : Possibilité d'ajouter de nouvelles mécaniques
