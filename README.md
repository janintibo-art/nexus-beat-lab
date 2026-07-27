# NEXUS Beat Lab

Boîte à rythmes / sampler pour Android (mode paysage, zoom au pincement).

L'interface est en HTML + Web Audio API, embarquée dans une application Android native (WebView). L'APK est compilé automatiquement par GitHub Actions à chaque `git push`.

## Récupérer l'APK

1. Pousser le code sur GitHub (voir plus bas).
2. Sur la page du dépôt : onglet **Actions** → dernier workflow **Build APK** → section **Artifacts** → télécharger `nexus-beat-lab-apk`.
3. Dézipper, transférer `app-debug.apk` sur le téléphone, l'installer (autoriser les sources inconnues).

## Étape 1 (cette version)

- 16 pads jouables (sons synthétisés : kick, snare, clap, hats, toms, bass, sub...)
- Séquenceur 16 pas × 6 pistes, avec mute par piste et surlignage du pas en cours
- BPM réglable (glisser sur le chiffre) + TAP tempo
- Swing, mixeur à faders, volume master, vumètre animé
- Menu : sauvegarde / rechargement du pattern (stockage local), pattern de démo
- Boutons et potentiomètres en relief, zoom au pincement

## Prochaines étapes prévues

1. Écran SAMPLE : chargement de fichiers WAV, affichage de la forme d'onde, slicer
2. Synthé BASS complet (cutoff, résonance, glide, drive...)
3. Effets : reverb, delay, distorsion, compresseur + envois FX
4. Mode SONG : enchaînement de patterns, banques B/C/D
5. Enregistrement en direct (ROLL, quantisation) et export audio

## Développement local

Ouvrir `app/src/main/assets/www/index.html` dans un navigateur suffit pour tester l'interface sans compiler l'APK.
