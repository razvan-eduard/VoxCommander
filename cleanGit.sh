#!/bin/bash

REMOTE="origin"

# Aici ținem minte cuvintele sub formatul |tip:cuvant|tip:cuvant|
seen_words="|"

# CUVINTE DE IGNORAT pentru ramurile lăsate la deduplicare
ignore_words=" add added update updated fix fixed remove removed create created implement use move moved clean test better temp force improve improved make "

echo "Sincronizăm cu serverul remote ($REMOTE)..."
git fetch $REMOTE --prune
echo "-----------------------------------"
echo "Analizăm branch-urile REMOTE..."
echo "-----------------------------------"

# Parcurgem referințele remote în loc de cele locale
git for-each-ref --sort=-committerdate refs/remotes/$REMOTE/ --format='%(refname:short)' | while read -r remote_branch; do

    # 'remote_branch' vine sub forma 'origin/feature/nume'. Extragem doar 'feature/nume'
    branch="${remote_branch#$REMOTE/}"

    # Sărim peste HEAD-ul remote-ului (ex: origin/HEAD)
    if [[ "$branch" == "HEAD" ]]; then continue; fi

    # 1. Verificăm tipul branch-ului
    if [[ ! "$branch" =~ ^(bugfix|chore|feature|hotfix|refactor)/ ]]; then
        continue
    fi

    # Extragem tipul și descrierea
    branch_type="${branch%%/*}"
    branch_desc="${branch#*/}"

    is_duplicate=0
    matched_reason=""
    
    # 2. Regulă specială: chore și hotfix se șterg implicit
    if [[ "$branch_type" == "chore" || "$branch_type" == "hotfix" ]]; then
        is_duplicate=1
        matched_reason="Tip implicit ($branch_type)"
    else
        # 3. Logica de cuvinte comune pentru bugfix, feature și refactor
        clean_desc=$(echo "$branch_desc" | tr '[:upper:]' '[:lower:]' | sed 's/[-_]/ /g')
        
        for word in $clean_desc; do
            if [ ${#word} -le 2 ]; then continue; fi
            if [[ "$ignore_words" == *" $word "* ]]; then continue; fi

            if [[ "$seen_words" == *"|$branch_type:$word|"* ]]; then
                is_duplicate=1
                matched_reason="Cuvânt comun: '$word'"
                break 
            fi
        done
    fi

    # 4. Executarea acțiunii pe REMOTE
    if [ $is_duplicate -eq 1 ]; then
        # Comanda de ștergere efectivă pe server. 
        # Este COMENTATĂ pentru siguranță. Șterge '#' pentru a o activa.
        
        # if git push $REMOTE --delete "$branch" >/dev/null 2>&1; then
        #     echo -e "\033[31m[ȘTERS DE PE REMOTE]\033[0m $branch ($matched_reason)"
        # else
        #     echo -e "\033[33m[EROARE REMOTE]\033[0m Nu am putut șterge $branch de pe server."
        # fi
        
        # Linia de mai jos e doar pentru afișare (Dry Run). 
        # Șterge-o sau comenteaz-o dacă activezi blocul 'if' de mai sus.
        echo -e "\033[31m[DE ȘTERS REMOTE]\033[0m $branch ($matched_reason)"
    else
        echo -e "\033[32m[PĂSTRAT REMOTE]\033[0m $branch"
        
        # Salvăm cuvintele
        clean_desc=$(echo "$branch_desc" | tr '[:upper:]' '[:lower:]' | sed 's/[-_]/ /g')
        for word in $clean_desc; do
            if [ ${#word} -gt 2 ] && [[ ! "$ignore_words" == *" $word "* ]]; then
                seen_words="${seen_words}${branch_type}:${word}|"
            fi
        done
    fi
done

echo "-----------------------------------"
echo "Curățenie remote finalizată."