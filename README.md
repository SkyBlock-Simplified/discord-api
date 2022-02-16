# SkyBlock Simplified Bot

[![Support Server Invite](https://img.shields.io/discord/652148034448261150.svg?color=7289da&label=SkyBlock%20Simplified&logo=discord)](https://discord.gg/sbs)

SkyBlock Simplified is a fast and powerful Hypixel SkyBlock Discord Bot that offers a plethora of features for users and
servers alike.

## Setup

- Create environment variables whenever you run a new Gradle task. Look at `.env_default` as an example list

## The Math

### Damage Per Hit

Equations:

```
damageMultiplier = (5 + weaponDamage)
                   (1 + 0.04 (combatLevel <= 50) + 0.01 (combatLevel > 50) + enchantsBonus + weaponBonus)
                   (armorBonus)
intermediateExpression = (1 + critDamage / 100)
                         (1 + strength / 100)
damagePerHit = damageMultiplier * intermediateExpression
```

Simplify the intermediate expression:

```
intermediateExpression = (1 + critDamage / 100)
                         (1 + strength / 100)
                       = (100 + critDamage)
                         (100 + strength)
                         (1 / 10_000)
```

Factor out 10_000, and you're left with the basic expression which must be maximized:

```
(100 + critDamage) (100 + strength)
```

Cplex takes in up to three types of problems: constants, linear expressions, and quadratic expressions. Reformat the 
problem for Cplex:

```
10_000 + 100 * critDamage + 100 * strength + critDamage * strength
\____/   \______________/   \____________/   \___________________/
 const        linear            linear             quadratic
```

Rearrange as an example with three reforges with x_ij, where x=reforge count, i=which reforge, and j=the stat:

```
10_000                              | const (no variables)
+ 100x_11 + 100x_21 + 100x_31       | linear (one variable per term)
+ 100x_12 + 100x_22 + 100x_32       | .
+ x_11*x_12 + x_11*x_22 + x_11*x_32 | quadratic (two variables per term)
+ x_21*x_12 + x_21*x_22 + x_21*x_32 | .
+ x_31*x_12 + x_31*x_22 + x_31*x_32 | .
```

Optaplanner allocates reforges to items. We must set up each reforge as an object that can be assigned. This is very
easy to do; just sum the stats for a particular object, add 100, and multiply against the other stat + 100. However,
this approach is ineffecient. Optaplanner supports incremental solving, meaning that if a single reforge is changed,
only that part of the equation should be reevaluated. To support this, expand the equation into individual terms and use
a `.join` function to connect terms. Then, use a `HundredReforgeProblemFact` to emulate the 100 + base player stat and 
treat it as a regular item/reforge.

```
100*100 + 100x_12 + 100x_22 + 100x_32
+ x_11*100 + x_11*x_12 + x_11*x_22 + x_11*x_32
+ x_21*100 + x_21*x_12 + x_21*x_22 + x_21*x_32
+ x_31*100 + x_31*x_12 + x_31*x_22 + x_31*x_32
```

### Damage Per Second

Similar to damage per hit, but the equation has more variables, so Cplex can't be used. Additionally, we have to account 
for crit chance:

```
damageMultiplier = (5 + weaponDamage)
                   (1 + 0.04 (combatLevel <= 50) + 0.01 (combatLevel > 50) + enchantsBonus + weaponBonus)
                   (armorBonus)
averageDamagePerHit = damageMultiplier (1 + str / 100) (1 + cd / 100) (1 + cc / 100) 
                      + damageMultiplier (1 + str / 100) (1 - (1 + cc / 100))
                    = damageMultiplier (1 + str / 100) [(1 + cd / 100) (1 + cc / 100) - (cc / 100)]
hitsPerSecond = (2)
                (1 + fer / 100)
                (1 + as / 100)
damagePerSecond = averageDamagePerHit * hitsPerSecond
intermediateEquation = (1 + str / 100)
                       (1 + fer / 100)
                       (1 + as / 100)
                       [(1 + cd / 100) (1 + cc / 100) - (cc / 100)]
```
