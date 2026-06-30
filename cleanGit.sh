#!/bin/bash

# Aici ținem minte cuvintele sub formatul |tip:cuvant|tip:cuvant|
seen_words="|"

# CUVINTE DE IGNORAT: Aceste cuvinte NU vor fi considerate "cuvinte comune"
# Adaugă aici verbe sau cuvinte prea generice (separate prin spații, cu spațiu la început și final)
ignore_words=" add added update updated fix fixed remove removed create created implement use move moved clean test better temp force improve improved make "

echo "Analizăm branch-urile..."
echo "-----------------------------------"

git for-each-ref --sort=-committerdate refs/heads/ --format='%(refname:short)' | while read -r branch; do

    # 1. Verificăm tipul branch-ului
    if [[ ! "$branch" =~ ^(bugfix|chore|feature|hotfix|refactor)/ ]]; then
        continue
    fi

    # Extragem tipul (ex: refactor) și descrierea (ex: force-vulkan-headers)
    branch_type="${branch%%/*}"
    branch_desc="${branch#*/}"

    # Înlocuim cratimele și underscore-urile cu spații și trecem totul cu litere mici
    # Înlocuim cratimele și underscore-urile cu spații și trecem totul cu litere mici
    clean_desc=$(echo "$branch_desc" | tr '[:upper:]' '[:lower:]' | sed 's/[-_]/ /g')

    is_duplicate=0
    matched_word=""
    
    # 2. Iterăm prin fiecare cuvânt din descrierea branch-ului curent
    for word in $clean_desc; do
        # Sărim peste cuvinte foarte scurte (ex: v1, ui, a, in)
        if [ ${#word} -le 2 ]; then continue; fi
        
        # Sărim peste cuvintele comune din lista de ignorare
        if [[ "$ignore_words" == *" $word "* ]]; then continue; fi

        # Verificăm dacă am mai văzut ACEST cuvânt pentru ACEST tip de branch
        if [[ "$seen_words" == *"|$branch_type:$word|"* ]]; then
            is_duplicate=1
            matched_word="$word"
            break # Am găsit un cuvânt comun, nu mai are rost să căutăm
        fi
    done

    if [ $is_duplicate -eq 1 ]; then
        echo -e "\033[31m[DE ȘTERS]\033[0m $branch (Cuvânt comun: '$matched_word')"
        
        # git branch -D "$branch"
    else
        echo -e "\033[32m[PĂSTRAT]\033[0m $branch"
        
        # Adăugăm TOATE cuvintele valide ale acestui branch păstrat în memorie
        for word in $clean_desc; do
            if [ ${#word} -gt 2 ] && [[ ! "$ignore_words" == *" $word "* ]]; then
                seen_words="${seen_words}${branch_type}:${word}|"
            fi
        done
    fi
done

echo "-----------------------------------"
echo "Curățenie finalizată."