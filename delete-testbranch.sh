

if ! command -v gh &> /dev/null; then
  echo "Please install GitHub CLI (gh) tool first, then configure login."
  exit 1
fi

REPO="showpune/asset-manager-showpune"

# Get all remote branches with pagination support
branches=$(gh api repos/$REPO/branches --paginate --jq '.[] | select(.name | startswith("ma-") or startswith("copilot")) | .name')

# Get local branches that start with "ma-" or "copilot"
local_branches=$(git branch | grep -E '^\s*(ma-|copilot)' | sed 's/^\s*\*\?\s*//')

if [ -z "$branches" ] && [ -z "$local_branches" ]; then
  echo "No branch found"
  exit 0
fi

# Print branch list and confirm deletion
if [ -n "$branches" ]; then
  echo "Found remote branches:"
  echo "$branches"
fi

if [ -n "$local_branches" ]; then
  echo "Found local branches:"
  echo "$local_branches"
fi

echo "Deleting branches..."

# Delete remote branches
if [ -n "$branches" ]; then
  echo "Deleting remote branches..."
  for branch in $branches; do
    echo "delete remote Branch:$branch"
    gh api -X DELETE repos/$REPO/git/refs/heads/$branch
  done
fi

# Delete local branches
if [ -n "$local_branches" ]; then
  echo "Deleting local branches..."
  for branch in $local_branches; do
    echo "delete local Branch:$branch"
    git branch -D $branch
  done
fi

echo "All branches starting with 'ma-' or 'copilot' have been deleted!"